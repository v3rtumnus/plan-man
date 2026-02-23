package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.DividendRepository;
import at.v3rtumnus.planman.dao.FinancialProductRepository;
import at.v3rtumnus.planman.dao.FinancialTransactionRepository;
import at.v3rtumnus.planman.dao.UploadLogRepository;
import at.v3rtumnus.planman.dto.finance.UploadResult;
import at.v3rtumnus.planman.dto.finance.UploadResultDto;
import at.v3rtumnus.planman.dto.finance.UploadType;
import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import at.v3rtumnus.planman.entity.finance.UploadLog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceImportServiceTest {

    @Mock
    private FinancialProductRepository productRepository;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private UploadLogRepository uploadLogRepository;

    @InjectMocks
    private FinanceImportService importService;

    /**
     * Creates a minimal in-memory PDF containing the given lines of text.
     */
    private byte[] createPdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setLeading(14f);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private MockMultipartFile pdfFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }

    // --- duplicate prevention ---

    @Test
    void importFinanceFile_duplicateFilename_returnsDuplicate() throws IOException {
        byte[] pdfBytes = createPdf("Some content");
        MockMultipartFile file = pdfFile("20240101_Dividende_TEST.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename("20240101_Dividende_TEST.pdf"))
                .thenReturn(Optional.of(new UploadLog("20240101_Dividende_TEST.pdf", LocalDate.now())));

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.DUPLICATE);
        verify(dividendRepository, never()).save(any());
    }

    // --- IGNORED patterns ---

    @Test
    void importFinanceFile_orderbestaetigung_returnsIgnored() throws IOException {
        byte[] pdfBytes = createPdf("Irrelevant content");
        MockMultipartFile file = pdfFile("20240101_Orderbestaetigung.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.IGNORED);
    }

    @Test
    void importFinanceFile_steuerbescheinigung_returnsIgnored() throws IOException {
        byte[] pdfBytes = createPdf("Tax document");
        MockMultipartFile file = pdfFile("20240101_Steuerbescheinigung.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.IGNORED);
    }

    @Test
    void importFinanceFile_depotauszug_returnsIgnored() throws IOException {
        byte[] pdfBytes = createPdf("Depot content");
        MockMultipartFile file = pdfFile("20240101_Depotauszug.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.IGNORED);
    }

    // --- UNKNOWN ---

    @Test
    void importFinanceFile_unknownDocumentType_returnsUnknown() throws IOException {
        byte[] pdfBytes = createPdf("Unknown document content");
        // Valid 8-digit prefix but no known keyword in filename
        MockMultipartFile file = pdfFile("20240101_SomeRandomDoc.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.UNKNOWN);
    }

    // --- Dividend routing ---

    @Test
    void importFinanceFile_dividendFile_routesToDividendProcessor() throws IOException {
        String isin = "AT0000APOST4";
        byte[] pdfBytes = createPdf(
                "(" + isin + "/some text",
                "Valuta 15.06.2024",
                "  Endbetrag  EUR  45,30"
        );
        MockMultipartFile file = pdfFile("20240615_Dividende_APOST.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());
        when(productRepository.findById(isin)).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenReturn(new FinancialProduct(isin));

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.SUCCESS);
        verify(dividendRepository).save(any());
        verify(uploadLogRepository).save(any(UploadLog.class));
    }

    // --- German decimal parsing ---

    @Test
    void importFinanceFile_germanDecimalFormat_parsedCorrectly() throws IOException {
        // "1.234,56" should be parsed as 1234.56
        String isin = "DE000BASF111";
        byte[] pdfBytes = createPdf(
                "(" + isin + "/some text",
                "Valuta 01.03.2024",
                "  Endbetrag  EUR  1.234,56"
        );
        MockMultipartFile file = pdfFile("20240301_Dividende_BASF.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());
        when(productRepository.findById(isin)).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenReturn(new FinancialProduct(isin));

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.SUCCESS);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    // --- retrieveUploadLogs ---

    @Test
    void retrieveUploadLogs_returnsMappedDtos() {
        UploadLog log1 = new UploadLog("file1.pdf", LocalDate.of(2024, 1, 1));
        UploadLog log2 = new UploadLog("file2.pdf", LocalDate.of(2024, 2, 1));
        when(uploadLogRepository.findByOrderByImportedAtDesc()).thenReturn(List.of(log1, log2));

        var result = importService.retrieveUploadLogs();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFilename()).isEqualTo("file1.pdf");
        assertThat(result.get(1).getFilename()).isEqualTo("file2.pdf");
    }

    // --- Wertpapierabrechnung (processTransaction) ---

    @Test
    void importFinanceFile_wertpapierabrechnung_buyTransaction_returnsSuccess() throws IOException {
        String isin = "AT0000000001";
        byte[] pdfBytes = createPdf(
                "(" + isin + "/text",
                "Kauf Aktie ABC",
                "Valuta 15.06.2024 Valuta",
                "Endbetrag EUR 98,50",
                "Kurswert EUR 95,00",
                "Ordervolumen 10,00"
        );
        MockMultipartFile file = pdfFile("20240615_Wertpapierabrechnung_ABC.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());
        when(productRepository.findById(isin)).thenReturn(Optional.of(new FinancialProduct(isin)));

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.SUCCESS);
        assertThat(result.getType()).isEqualTo(UploadType.BUY);
        verify(financialTransactionRepository).save(any());
    }

    // --- KaufFondsZertifikate (processCertificateTransaction) ---

    @Test
    void importFinanceFile_verkaufFondsZertifikate_sellTransaction_returnsSuccess() throws IOException {
        String isin = "AT0000000002";
        byte[] pdfBytes = createPdf(
                "(" + isin + "/text",
                "Verkauf Fonds XYZ",
                "Valuta 15.06.2024 Valuta",
                "Endbetrag EUR 90,00",
                "Kurswert EUR 95,00",
                "Ausgef\u00fchrt 10"
        );
        MockMultipartFile file = pdfFile("20240615_VerkaufFondsZertifikate_XYZ.pdf", pdfBytes);

        when(uploadLogRepository.findByFilename(anyString())).thenReturn(Optional.empty());
        when(productRepository.findById(isin)).thenReturn(Optional.of(new FinancialProduct(isin)));

        UploadResultDto result = importService.importFinanceFile(file);

        assertThat(result.getResult()).isEqualTo(UploadResult.SUCCESS);
        assertThat(result.getType()).isEqualTo(UploadType.SELL);
        verify(financialTransactionRepository).save(any());
    }
}
