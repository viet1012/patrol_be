package com.example.patrol_be.constants;

public class PatrolConstants {

	// fac/plant = "SPC" => lấy dữ liệu tất cả nhà máy (không filter plant)
	public static final String ALL_FAC = "SPC";

	public static boolean isAllFac(String fac) {
		return fac != null && ALL_FAC.equalsIgnoreCase(fac.trim());
	}
}
