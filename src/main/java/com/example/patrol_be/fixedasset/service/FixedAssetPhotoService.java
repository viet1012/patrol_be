package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditPhotoResponse;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditPhotoTargetDto;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMasterPhotoDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetAuditRepository;
import com.example.patrol_be.common.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * "3) Thay QR" scan photo: the upload is written to a temp file in after/
 * first; inside the QR replace transaction it is renamed to
 * {Div}_{MachineCode}_{Floor_Act}_{AA_Act}_{Group}_3.jpg (Div / Group from
 * MASTER, *_Act of the audit row) and that file name becomes Photo3 of the
 * audit row (Photo3_Old = current MASTER Photo3; MASTER is not updated).
 * An existing file with the same name is renamed to {name}_{yyyyMMdd_HHmmss}.jpg.
 * When the transaction fails or does not use the photo, the
 * new file is deleted and the old one renamed back. A failed write never fails
 * the request: QR replace runs without photo and photoSaved = false.
 * Also the audit before photo upload (before/{MachineCode}_{yyyyMMdd_HHmmss}.jpg, file only).
 */
@Slf4j
@Service
public class FixedAssetPhotoService {
    private static final Set<String> AFTER_TYPES = Set.of(FixedAssetPhotoStorage.JPEG);
    private static final Set<String> BEFORE_TYPES =
            Set.of(FixedAssetPhotoStorage.JPEG, FixedAssetPhotoStorage.PNG);
    private static final String EXTENSION = ".jpg";
    private static final String TEMP_PREFIX = ".tmp_";
    private static final DateTimeFormatter OLD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final FixedAssetService fixedAssetService;
    private final FixedAssetAuditRepository auditRepository;
    private final FixedAssetPhotoStorage photoStorage;
    /** Per-MachineCode (trimmed, upper-case) lock of the photo flow. */
    private final ConcurrentHashMap<String, MachineLock> machineLocks = new ConcurrentHashMap<>();

    public FixedAssetPhotoService(
            FixedAssetService fixedAssetService,
            FixedAssetAuditRepository auditRepository,
            FixedAssetPhotoStorage photoStorage) {
        this.fixedAssetService = fixedAssetService;
        this.auditRepository = auditRepository;
        this.photoStorage = photoStorage;
    }

    public FixedAssetAuditSaveResponse saveAuditWithPhoto(
            FixedAssetAuditSaveRequest request,
            MultipartFile photo) {
        boolean hasPhoto = photo != null && !photo.isEmpty();
        if (hasPhoto) {
            photoStorage.validate(photo, AFTER_TYPES);
        }
        if (!hasPhoto || request == null || !Boolean.TRUE.equals(request.getQrReplace())) {
            return fixedAssetService.saveAudit(request);
        }

        // Rename / write / DB update (incl. commit) / undo of one machine run
        // one request at a time (this app instance only).
        String lockKey = trimToEmpty(request.getMachineCode()).toUpperCase(Locale.ROOT);
        MachineLock machineLock = acquireMachineLock(lockKey);
        try {
            PendingPhoto written = writePhoto(request, photo);
            if (written == null) {
                return fixedAssetService.saveAudit(request);
            }

            FixedAssetAuditSaveResponse response;
            try {
                response = fixedAssetService.saveAudit(request, written::place);
            } catch (RuntimeException exception) {
                written.undo();
                throw exception;
            }
            // Branch without write (old label, confirmation, ...): Photo3 untouched.
            if (!response.isPhotoSaved()) {
                written.undo();
            } else {
                response.setPhoto3Url(photoStorage.afterUrl(response.getPhoto3()));
            }
            return response;
        } finally {
            releaseMachineLock(lockKey, machineLock);
        }
    }

    /**
     * Before photo of one audit row: file before/{MachineCode}_{yyyyMMdd_HHmmss}.jpg
     * (MachineCode of the audit row); nothing is written to the DB.
     * Only the audit's creator or a user with FixedAsset.before may upload.
     * null when the audit row does not exist.
     */
    public FixedAssetAuditPhotoResponse uploadBeforePhoto(long auditId, String userId, MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Photo file is required");
        }
        photoStorage.validate(photo, BEFORE_TYPES);
        FixedAssetAuditPhotoTargetDto audit = auditRepository.findAuditPhotoTarget(auditId);
        if (audit == null) {
            return null;
        }
        fixedAssetService.requireAuditPhotoPermission(userId, audit.getUserId());

        Path target;
        try {
            Path dir = photoStorage.folder(StorageProperties.FIXED_ASSET_BEFORE);
            if (dir == null) {
                throw new IllegalStateException("Fixed Asset photo dir unavailable");
            }
            target = photoStorage.resolve(dir,
                    photoStorage.baseName(audit.getMachineCode(), LocalDateTime.now()) + EXTENSION);
        } catch (IOException exception) {
            throw new IllegalStateException("Fixed Asset photo dir unavailable", exception);
        }

        try {
            photoStorage.write(photo, target);
        } catch (FileAlreadyExistsException exception) {
            // Same machine + same second: the existing file is not ours.
            throw new IllegalStateException("Fixed Asset photo already exists: " + target.getFileName(), exception);
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new IllegalStateException("Fixed Asset photo was not saved", exception);
        }

        String photoPath = photoStorage.dbValue(StorageProperties.FIXED_ASSET_BEFORE, target);
        return new FixedAssetAuditPhotoResponse(auditId, photoPath, photoStorage.url(photoPath));
    }

    private MachineLock acquireMachineLock(String key) {
        MachineLock machineLock = machineLocks.compute(key, (k, existing) -> {
            MachineLock current = existing == null ? new MachineLock() : existing;
            current.holders++;
            return current;
        });
        machineLock.lock.lock();
        return machineLock;
    }

    /** Unlocks; the map entry is removed when no request holds or waits for it. */
    private void releaseMachineLock(String key, MachineLock machineLock) {
        machineLock.lock.unlock();
        machineLocks.computeIfPresent(key, (k, existing) -> --existing.holders == 0 ? null : existing);
    }

    /**
     * Writes the upload to a temp file in after/ (final name needs the audit
     * row's *_Act, known only in the transaction); null (logged) when it cannot be saved.
     */
    private PendingPhoto writePhoto(FixedAssetAuditSaveRequest request, MultipartFile photo) {
        String machineCode = trimToEmpty(request.getMachineCode());
        Path temp = null;
        try {
            // Checked per request: StorageConfig only warns when it cannot create the dir.
            Path dir = photoStorage.folder(StorageProperties.FIXED_ASSET_AFTER);
            if (dir == null) {
                log.warn("Fixed Asset photo not saved for {}: photo dir unavailable", machineCode);
                return null;
            }

            FixedAssetMasterPhotoDto master = auditRepository.findMasterPhoto(machineCode);
            if (master == null) {
                log.warn("Fixed Asset photo not saved for {}: machine not in MASTER", machineCode);
                return null;
            }

            temp = photoStorage.resolve(dir, TEMP_PREFIX + UUID.randomUUID() + EXTENSION);
            photoStorage.write(photo, temp);
            return new PendingPhoto(dir, master, temp);
        } catch (IOException | RuntimeException exception) {
            log.warn("Fixed Asset photo not saved for {}", machineCode, exception);
            if (temp != null) {
                deleteQuietly(temp);
            }
            return null;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Cannot delete Fixed Asset photo {}", file, exception);
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** holders (requests holding or waiting) is only changed inside map compute calls. */
    private static final class MachineLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int holders;
    }

    /**
     * Uploaded "3) Thay QR" photo of one request. temp: upload in after/;
     * file: final photo once placed (null = not placed); renamedOld: existing
     * file of the same name after rename (null = none), restored to file on undo.
     */
    private final class PendingPhoto {
        private final Path dir;
        private final FixedAssetMasterPhotoDto master;
        private final Path temp;
        private Path file;
        private Path renamedOld;

        private PendingPhoto(Path dir, FixedAssetMasterPhotoDto master, Path temp) {
            this.dir = dir;
            this.master = master;
            this.temp = temp;
        }

        /**
         * Photo3Update: temp -> {Div}_{MachineCode}_{Floor_Act}_{AA_Act}_{Group}_3.jpg
         * (an existing file of that name is renamed to {name}_{yyyyMMdd_HHmmss}.jpg
         * first); returns the file name, null (logged, files restored) on failure.
         */
        String place(String floorAct, String positionAAAct) {
            String baseName = photoStorage.photo3BaseName(
                    master.getDiv(), master.getMachineCode(), floorAct, positionAAAct, master.getGroup());
            try {
                Path target = photoStorage.resolve(dir, baseName + EXTENSION);
                if (Files.exists(target)) {
                    Path oldName = photoStorage.resolve(dir, baseName + "_"
                            + LocalDateTime.now().format(OLD_TIME_FORMAT) + EXTENSION);
                    // Fails (no overwrite) if that name already exists.
                    Files.move(target, oldName);
                    renamedOld = oldName;
                    file = target;
                }
                Files.move(temp, target);
                file = target;
                return target.getFileName().toString();
            } catch (IOException | RuntimeException exception) {
                log.warn("Fixed Asset photo not placed for {}", master.getMachineCode(), exception);
                undo();
                return null;
            }
        }

        /** Deletes the new / temp file, then renames the old file back to its original name. */
        void undo() {
            // file exists only after the temp was moved there (or was renamed away).
            if (file != null && !Files.exists(temp)) {
                deleteQuietly(file);
            }
            deleteQuietly(temp);
            if (renamedOld != null) {
                try {
                    Files.move(renamedOld, file);
                } catch (IOException exception) {
                    log.warn("Cannot rename Fixed Asset photo {} back to {}", renamedOld, file, exception);
                }
            }
            file = null;
            renamedOld = null;
        }
    }
}
