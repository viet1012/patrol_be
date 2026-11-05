package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditPhotoResponse;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditPhotoTargetDto;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMasterPhotoDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetAuditRepository;
import com.example.patrol_be.common.config.FixedAssetPhotoFolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File names of every folder come from FixedAssetPhotoStorage.photoFileName:
 * "{qr}_3.jpg" (raw QR content sent by FE, cleaned) when it contains the
 * MachineCode, else a fallback built from MASTER; an existing file with the
 * same name is renamed to {name}_{yyyyMMdd_HHmmss}.jpg first.
 * "3) Thay QR" step 2 photo: the upload is written to a temp file in after/
 * first; inside the QR replace transaction it is renamed to the new QR name
 * (fallback {Div}_{MachineCode}_{Floor_Act}_{AA_Act}_{Group}_3.jpg, *_Act of
 * the audit row) and that file name becomes Photo3 of the audit row
 * (Photo3_Old = current MASTER Photo3; MASTER is not updated).
 * When the transaction fails or does not use the photo, the
 * new file is deleted and the old one renamed back. A failed write never fails
 * the request: QR replace runs without photo and photoSaved = false.
 * Also the audit photo upload (audit/ or before/, file only).
 */
@Slf4j
@Service
public class FixedAssetPhotoService {
    private static final Set<String> AFTER_TYPES = Set.of(FixedAssetPhotoStorage.JPEG);
    private static final Set<String> BEFORE_TYPES =
            Set.of(FixedAssetPhotoStorage.JPEG, FixedAssetPhotoStorage.PNG);
    private static final String TEMP_PREFIX = ".tmp_";

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
     * Photo of one audit row (file only, nothing written to the DB):
     * {folder}/{qr}_3.jpg; missing qr or qr without the audit row's MachineCode
     * -> {Div}_{MachineCode}_{Floor}_{PositionAA}_{Group}_3.jpg from MASTER.
     * Same name already there: the old file is renamed to {name}_{yyyyMMdd_HHmmss}.jpg.
     * folder: AUDIT (Kiểm kê) or BEFORE (Thay QR step 1); AFTER is only
     * written by the QR replace flow (saveAuditWithPhoto), so it is rejected here.
     * Only the audit's creator or a user with FixedAsset.before may upload.
     * null when the audit row does not exist.
     */
    public FixedAssetAuditPhotoResponse uploadAuditPhoto(
            long auditId, String userId, FixedAssetPhotoFolder folder, String qr, MultipartFile photo) {
        if (folder == null || folder == FixedAssetPhotoFolder.AFTER) {
            throw new IllegalArgumentException("Fixed Asset photo folder not allowed for this upload: " + folder
                    + " (allowed: " + FixedAssetPhotoFolder.AUDIT + ", " + FixedAssetPhotoFolder.BEFORE + ")");
        }
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Photo file is required");
        }
        photoStorage.validate(photo, BEFORE_TYPES);
        FixedAssetAuditPhotoTargetDto audit = auditRepository.findAuditPhotoTarget(auditId);
        if (audit == null) {
            return null;
        }
        fixedAssetService.requireAuditPhotoPermission(userId, audit.getUserId());

        String machineCode = trimToEmpty(audit.getMachineCode());
        // Same lock as the QR replace flow: same-name rename + write of one machine run one at a time.
        String lockKey = machineCode.toUpperCase(Locale.ROOT);
        MachineLock machineLock = acquireMachineLock(lockKey);
        try {
            Path dir;
            try {
                dir = photoStorage.folder(folder);
            } catch (IOException exception) {
                throw new IllegalStateException("Fixed Asset photo dir unavailable", exception);
            }
            if (dir == null) {
                throw new IllegalStateException("Fixed Asset photo dir unavailable");
            }

            FixedAssetMasterPhotoDto master = auditRepository.findMasterPhoto(machineCode);
            String fileName = master == null
                    ? photoStorage.photoFileName(qr, machineCode, null, null, null, null)
                    : photoStorage.photoFileName(qr, machineCode,
                            master.getDiv(), master.getFloor(), master.getPositionAA(), master.getGroup());
            Path target = photoStorage.resolve(dir, fileName);

            Path renamedOld;
            try {
                renamedOld = photoStorage.moveExistingAside(target);
            } catch (IOException exception) {
                throw new IllegalStateException("Existing Fixed Asset photo could not be renamed: " + fileName, exception);
            }
            try {
                photoStorage.write(photo, target);
            } catch (IOException exception) {
                // FileAlreadyExists: the file at target is not ours, keep it.
                if (!(exception instanceof FileAlreadyExistsException)) {
                    deleteQuietly(target);
                    photoStorage.restoreAside(renamedOld, target);
                }
                throw new IllegalStateException("Fixed Asset photo was not saved", exception);
            }

            String photoPath = photoStorage.dbValue(folder, target);
            return new FixedAssetAuditPhotoResponse(auditId, photoPath, photoStorage.url(photoPath));
        } finally {
            releaseMachineLock(lockKey, machineLock);
        }
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
            Path dir = photoStorage.folder(FixedAssetPhotoFolder.AFTER);
            if (dir == null) {
                log.warn("Fixed Asset photo not saved for {}: photo dir unavailable", machineCode);
                return null;
            }

            FixedAssetMasterPhotoDto master = auditRepository.findMasterPhoto(machineCode);
            if (master == null) {
                log.warn("Fixed Asset photo not saved for {}: machine not in MASTER", machineCode);
                return null;
            }

            temp = photoStorage.resolve(dir, TEMP_PREFIX + UUID.randomUUID() + FixedAssetPhotoStorage.EXTENSION);
            photoStorage.write(photo, temp);
            return new PendingPhoto(dir, master, request.getQr(), temp);
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
        /** Raw content of the new QR (request.qr). */
        private final String qr;
        private final Path temp;
        private Path file;
        private Path renamedOld;

        private PendingPhoto(Path dir, FixedAssetMasterPhotoDto master, String qr, Path temp) {
            this.dir = dir;
            this.master = master;
            this.qr = qr;
            this.temp = temp;
        }

        /**
         * Photo3Update: temp -> {qr}_3.jpg (fallback
         * {Div}_{MachineCode}_{Floor_Act}_{AA_Act}_{Group}_3.jpg); an existing file
         * of that name is renamed to {name}_{yyyyMMdd_HHmmss}.jpg first. Returns the
         * file name (= audit Photo3), null (logged, files restored) on failure.
         */
        String place(String floorAct, String positionAAAct) {
            String fileName = photoStorage.photoFileName(qr, master.getMachineCode(),
                    master.getDiv(), floorAct, positionAAAct, master.getGroup());
            try {
                Path target = photoStorage.resolve(dir, fileName);
                renamedOld = photoStorage.moveExistingAside(target);
                if (renamedOld != null) {
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
            photoStorage.restoreAside(renamedOld, file);
            file = null;
            renamedOld = null;
        }
    }
}
