package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.finance.UploadResult;
import at.v3rtumnus.planman.dto.finance.UploadResultDto;
import at.v3rtumnus.planman.service.FinanceImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Controller
@RequestMapping("/api/finance")
@Slf4j
public class FinanceApiController {

    public static final String APPLICATION_PDF = "application/pdf";

    @Autowired
    private FinanceImportService financeImportService;

    @PostMapping
    public @ResponseBody UploadResultDto uploadFinanceFile(@RequestParam("file") MultipartFile file) {
        if (!Objects.equals(file.getContentType(), APPLICATION_PDF)) {
            return UploadResultDto.builder()
                    .filename(file.getOriginalFilename())
                    .result(UploadResult.FAILURE)
                    .error("Wrong file type")
                    .build();
        }

        return financeImportService.importFinanceFile(file);
    }
}
