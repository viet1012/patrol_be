package com.example.patrol_be;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ExcelService excelService;


    @PostMapping
    public String saveReport(
            @RequestParam("division") String division,
            @RequestParam("group") String group,
            @RequestParam("machine") String machine,
            @RequestParam("comment") String comment,
            @RequestParam("reason1") String reason1,
            @RequestParam("reason2") String reason2,
            @RequestParam("image") MultipartFile imageFile) {

        try {
            // Tạo ReportRequest từ các param
            ReportRequest request = new ReportRequest();
            request.setDivision(division);
            request.setGroup(group);
            request.setMachine(machine);
            request.setComment(comment);
            request.setReason1(reason1);
            request.setReason2(reason2);

            // Gọi service, truyền thêm MultipartFile imageFile (bạn cần cập nhật ExcelService)
            excelService.appendToExcel(request, imageFile);

            return "{\"status\":\"success\"}";
        } catch (Exception e) {
            return "{\"status\":\"error\", \"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
