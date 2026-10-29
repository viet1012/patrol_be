package com.example.patrol_be.patrol.service;

import com.example.patrol_be.patrol.repository.HSEPatrolGroupMasterRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Xác định PIC của Patrol từ HSE_Patrol_Group_Master.
 * Thứ tự ưu tiên: MacID → Cate → Area → Grp, luôn trong phạm vi Plant + Grp (Grp = division).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PicResolver {

	private final HSEPatrolGroupMasterRepo masterRepo;

	public String resolve(String plant, String grp, String area, String macId) {
		String p = normalizeKey(plant);
		String g = normalizeKey(grp);
		String a = normalizeKey(area);
		String m = normalizeKey(macId);

		log.debug("Resolve PIC input: plant={}, grp={}, area={}, macId={}", p, g, a, m);

		if (p == null || g == null) {
			log.debug("Resolve PIC skipped: plant or grp is empty");
			return null;
		}

		String pic;

		if (m != null) {
			pic = matched("MacID", masterRepo.findPicLevelMacId(p, g, m));
			if (pic != null) return pic;

			pic = matched("Cate", masterRepo.findPicLevelCate(p, g, m));
			if (pic != null) return pic;
		}

		if (a != null) {
			pic = matched("Area", masterRepo.findPicLevelArea(p, g, a));
			if (pic != null) return pic;
		}

		pic = matched("Grp", masterRepo.findPicLevelGrp(p, g));
		if (pic != null) return pic;

		log.debug("Resolve PIC: no PIC found");
		return null;
	}

	private String matched(String level, String pic) {
		String value = clean(pic);
		if (value != null) {
			log.debug("Resolve PIC matched at level {}: pic={}", level, value);
		}
		return value;
	}

	/** Trim, bỏ NBSP, UPPER; chuỗi rỗng → null. */
	private static String normalizeKey(String value) {
		String cleaned = clean(value);
		return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
	}

	private static String clean(String value) {
		if (value == null) return null;
		String cleaned = value.replace(' ', ' ').trim();
		return cleaned.isEmpty() ? null : cleaned;
	}
}
