package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMasterPhotoDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetAuditRepository;
import com.example.patrol_be.fixedasset.service.FixedAssetService.Photo3Update;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * "3) Thay QR" scan photo: the file is written first, then the QR replace
 * transaction sets it as MASTER Photo3. An existing file with the same name is
 * renamed to *_old_{yyyyMMddHHmmss}.jpg (= Photo3_Old). When the transaction
 * fails or does not use the photo, the new file is deleted and the old one
 * renamed back. A failed write never fails the request: QR replace runs
 * without photo and photoSaved = false.
 */
@Slf4j
@Service
public class FixedAssetPhotoService {
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final String PHOTO_NO = "3";
    private static final String EXTENSION = ".jpg";
    private static final DateTimeFormatter OLD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // Windows-invalid file name characters and control characters.
    private static final String INVALID_FILE_NAME_CHARS = "[\\\\/:*?\"<>|\\p{Cntrl}]";

    private final FixedAssetService fixedAssetService;
    private final FixedAssetAuditRepository auditRepository;
    /** null when fixed-asset.photo-dir is blank, cannot be created or is not writable. */
    private final Path photoDir;
    /** Per-MachineCode (trimmed, upper-case) lock of the photo flow. */
    private final ConcurrentHashMap<String, MachineLock> machineLocks = new ConcurrentHashMap<>();

    public FixedAssetPhotoService(
            FixedAssetService fixedAssetService,
            FixedAssetAuditRepository auditRepository,
            @Value("${fixed-asset.photo-dir:}") String photoDir) {
        this.fixedAssetService = fixedAssetService;
        this.auditRepository = auditRepository;
        this.photoDir = initPhotoDir(photoDir);
    }

    public FixedAssetAuditSaveResponse saveAuditWithPhoto(
            FixedAssetAuditSaveRequest request,
            MultipartFile photo) {
        boolean hasPhoto = photo != null && !photo.isEmpty();
        if (hasPhoto) {
            validatePhoto(photo);
        }
        if (!hasPhoto || request == null || !Boolean.TRUE.equals(request.getQrReplace())) {
            return fixedAssetService.saveAudit(request);
        }

        // Rename / write / DB update (incl. commit) / undo of one machine run
        // one request at a time (this app instance only).
        String lockKey = trimToEmpty(request.getMachineCode()).toUpperCase(Locale.ROOT);
        MachineLock machineLock = acquireMachineLock(lockKey);
        try {
            WrittenPhoto written = writePhoto(request, photo);
            if (written == null) {
                return fixedAssetService.saveAudit(request);
            }

            FixedAssetAuditSaveResponse response;
            try {
                response = fixedAssetService.saveAudit(request, new Photo3Update(
                        fileName(written.file()),
                        written.renamedOld() == null ? null : fileName(written.renamedOld())));
            } catch (RuntimeException exception) {
                undo(written);
                throw exception;
            }
            // Branch without write (old label, confirmation, ...): Photo3 untouched.
            if (!response.isPhotoSaved()) {
                undo(written);
            }
            return response;
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

    private static Path initPhotoDir(String configured) {
        if (configured == null || configured.isBlank()) {
            log.warn("fixed-asset.photo-dir is not set: Fixed Asset photos will not be saved");
            return null;
        }
        Path dir = Paths.get(configured.trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException | RuntimeException exception) {
            log.warn("Cannot create Fixed Asset photo dir {}: photos will not be saved", dir, exception);
            return null;
        }
        if (!Files.isWritable(dir)) {
            log.warn("Fixed Asset photo dir {} is not writable: photos will not be saved", dir);
            return null;
        }
        log.info("Fixed Asset photo dir: {}", dir);
        return dir;
    }

    private static void validatePhoto(MultipartFile photo) {
        if (!JPEG_CONTENT_TYPE.equalsIgnoreCase(photo.getContentType())) {
            throw new IllegalArgumentException("Photo must be image/jpeg");
        }
        if (photo.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Photo must be at most 5MB");
        }
    }

    /**
     * Renames an existing file of the same name to *_old_{time}.jpg, then
     * writes the photo; null (logged, old file restored) when it cannot be saved.
     */
    private WrittenPhoto writePhoto(FixedAssetAuditSaveRequest request, MultipartFile photo) {
        String machineCode = trimToEmpty(request.getMachineCode());
        if (photoDir == null) {
            log.warn("Fixed Asset photo not saved for {}: photo dir unavailable", machineCode);
            return null;
        }

        Path target = null;
        Path renamedOld = null;
        boolean writing = false;
        try {
            FixedAssetMasterPhotoDto master = auditRepository.findMasterPhoto(machineCode);
            if (master == null) {
                log.warn("Fixed Asset photo not saved for {}: machine not in MASTER", machineCode);
                return null;
            }

            String baseName = buildBaseName(master, request);
            target = resolveInDir(baseName + EXTENSION);
            if (Files.exists(target)) {
                Path oldName = resolveInDir(baseName + "_old_"
                        + LocalDateTime.now().format(OLD_TIME_FORMAT) + EXTENSION);
                // Fails (no overwrite) if that *_old_ name already exists.
                Files.move(target, oldName);
                renamedOld = oldName;
            }
            try (InputStream in = photo.getInputStream()) {
                writing = true;
                Files.copy(in, target);
            }
            return new WrittenPhoto(target, renamedOld);
        } catch (IOException | RuntimeException exception) {
            log.warn("Fixed Asset photo not saved for {}", machineCode, exception);
            // Partial new file removed only when this request created it (a file
            // created meanwhile by another request is kept).
            boolean ownFile = writing && !(exception instanceof FileAlreadyExistsException);
            undo(new WrittenPhoto(ownFile ? target : null, renamedOld, target));
            return null;
        }
    }

    /** {Div}_{MachineCode}_{Floor}_{PositionAA}_{Group}_3 (no .jpg), QR (new) location. */
    private static String buildBaseName(FixedAssetMasterPhotoDto master, FixedAssetAuditSaveRequest request) {
        String name = String.join("_",
                trimToEmpty(master.getDiv()),
                trimToEmpty(master.getMachineCode()),
                trimToEmpty(request.getQrFloor()),
                trimToEmpty(request.getQrPositionAA()),
                trimToEmpty(master.getGroup()),
                PHOTO_NO);
        return name.replaceAll(INVALID_FILE_NAME_CHARS, "_");
    }

    private Path resolveInDir(String fileName) {
        Path file = photoDir.resolve(fileName).normalize();
        if (!photoDir.equals(file.getParent())) {
            throw new IllegalArgumentException("Invalid Fixed Asset photo file name: " + fileName);
        }
        return file;
    }

    /** Deletes the new file, then renames the old file back to its original name. */
    private static void undo(WrittenPhoto written) {
        if (written.file() != null) {
            try {
                Files.deleteIfExists(written.file());
            } catch (IOException exception) {
                log.warn("Cannot delete Fixed Asset photo {}", written.file(), exception);
            }
        }
        if (written.renamedOld() != null) {
            try {
                Files.move(written.renamedOld(), written.originalName());
            } catch (IOException exception) {
                log.warn("Cannot rename Fixed Asset photo {} back to {}",
                        written.renamedOld(), written.originalName(), exception);
            }
        }
    }

    private static String fileName(Path file) {
        return file.getFileName().toString();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * file: new photo (null = not written by this request); renamedOld: old
     * file of the same name after rename (null = none); originalName: its name
     * before rename (= the new photo's name).
     */
    /** holders (requests holding or waiting) is only changed inside map compute calls. */
    private static final class MachineLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int holders;
    }

    private record WrittenPhoto(Path file, Path renamedOld, Path originalName) {
        WrittenPhoto(Path file, Path renamedOld) {
            this(file, renamedOld, file);
        }
    }
}
