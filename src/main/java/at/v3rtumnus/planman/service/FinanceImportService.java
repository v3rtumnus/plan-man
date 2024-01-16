package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.DividendRepository;
import at.v3rtumnus.planman.dao.FinancialProductRepository;
import at.v3rtumnus.planman.dao.FinancialTransactionRepository;
import at.v3rtumnus.planman.dao.UploadLogRepository;
import at.v3rtumnus.planman.dto.finance.UploadLogDto;
import at.v3rtumnus.planman.dto.finance.UploadResult;
import at.v3rtumnus.planman.dto.finance.UploadResultDto;
import at.v3rtumnus.planman.dto.finance.UploadType;
import at.v3rtumnus.planman.entity.finance.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceImportService {

    private final FinancialProductRepository productRepository;
    private final DividendRepository dividendRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final UploadLogRepository uploadLogRepository;

    public List<UploadLogDto> retrieveUploadLogs() {
        return uploadLogRepository.findByOrderByImportedAtDesc()
                .stream()
                .map(uploadLog -> new UploadLogDto(uploadLog.getFilename(), uploadLog.getImportedAt()))
                .collect(Collectors.toList());
    }

    public UploadResultDto importFinanceFile(MultipartFile file) {
        log.info("Importing " + file.getOriginalFilename());

        if (uploadLogRepository.findByFilename(file.getOriginalFilename()).isPresent()) {
            log.warn("Not importing file as it was already imported");

            return UploadResultDto
                    .builder()
                    .filename(file.getOriginalFilename())
                    .result(UploadResult.DUPLICATE)
                    .build();
        }


        List<String> lines = getTextFromPDF(file);

        UploadResultDto result = processShareFile(Objects.requireNonNull(file.getOriginalFilename()), lines);

        log.info("Successfully imported " + result.getFilename());

        return result;
    }

    private List<String> getTextFromPDF(MultipartFile file) {
        List<String> lines;
        try (PDDocument document = Loader.loadPDF(file.getInputStream())) {
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);

            PDFTextStripper tStripper = new PDFTextStripper();

            String pdfFileInText = tStripper.getText(document);

            lines = Arrays.stream(pdfFileInText.split("\\r?\\n")).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return lines;
    }

    private UploadResultDto processShareFile(String filename, List<String> lines) {
        if (!filename.matches("^\\d{8}_.*$") || filename.contains("Orderbestaetigung") ||
            filename.contains("Steuerbescheinigung") || filename.contains("Kontoauszug") ||
            filename.contains("Hauptversammlung") || filename.contains("BestaetigungAenderungRisikoklasse") ||
            filename.contains("Kundenanschreiben") || filename.contains("Saldenmitteilung") ||
            filename.contains("Auftragsstreichung") || filename.contains("Depotauszug") ||
            filename.contains("Serienanschreiben") || filename.contains("Anschreiben") ||
            filename.contains("Konto-Depotinformation") || filename.contains("MiFIDKosteninformation") ||
            filename.contains("Spitzenregulierung") || filename.contains("Mahnwesen") ||
            filename.contains("AGB") || filename.contains("Willkommensbrief") ||
            filename.contains("BestaetigungderAdressaenderung") || filename.contains("Kreditangebot") ||
            filename.contains("MIFID-Verlustschwellenmeldung") || filename.contains("BestaetigungAnlageMandat") ||
            (filename.contains("KaufFondsZertifikate") && lines.stream().anyMatch(s -> s.contains("Sammelabrechnung")))) {
            return UploadResultDto
                    .builder()
                    .filename(filename)
                    .result(UploadResult.IGNORED)
                    .build();
        }
        if (filename.contains("Fondsthesaurierung") || filename.contains("Fondsertragsausschuettung") ||
            filename.contains("Dividende")) {
            return processDividend(filename, lines);
        }
        if (filename.contains("Wertpapierabrechnung")) {
            return processTransaction(filename, lines);
        }
        if (filename.contains("KaufFondsZertifikate") || filename.contains("VerkaufFondsZertifikate")) {
            return processCertificateTransaction(filename, lines);
        }

        return UploadResultDto
                .builder()
                .result(UploadResult.UNKNOWN)
                .build();
    }

    private UploadResultDto processCertificateTransaction(String filename, List<String> lines) {
        boolean buy = lines.stream().anyMatch(s -> s.contains("Kauf"));
        LocalDate date = null;
        BigDecimal amount = null;
        BigDecimal value = null;
        BigDecimal quantity = null;
        String isin = null;
        Pattern isinPattern = Pattern.compile("\\((.{12})/");
        for (String l : lines) {
            if (l.contains("Valuta ")) {
                Matcher matcher = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}").matcher(l);

                if (matcher.find()) {
                    date = LocalDate.parse(matcher.group(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }
            if (l.contains("Endbetrag") && l.contains("EUR")) {
                Matcher matcher = Pattern.compile("-?\\d*\\.?\\d+,\\d+").matcher(l);

                if (matcher.find()) {
                    amount = new BigDecimal(matcher.group().replace(".", "").replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }
            if (l.contains("Kurswert") && l.contains("EUR")) {
                Matcher matcher = Pattern.compile("\\d*\\.?\\d+,\\d+").matcher(l.substring(l.indexOf("Kurswert")));

                if (matcher.find()) {
                    value = new BigDecimal(matcher.group().replace(".", "").replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }
            if (l.startsWith("Ausgeführt")) {
                Matcher matcher = Pattern.compile("\\d+(,\\d+)?").matcher(l);

                if (matcher.find()) {
                    quantity = new BigDecimal(matcher.group().replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }

            Matcher isinMatcher = isinPattern.matcher(l);

            if (isinMatcher.find()) {
                isin = isinMatcher.group(1);
            }
        }

        FinancialProduct product = getOrCreateFinancialProduct(isin);
        FinancialTransaction transaction = new FinancialTransaction(date,
                value,
                quantity,
                buy ? Objects.requireNonNull(amount).abs().subtract(value) : Objects.requireNonNull(value).subtract(amount),
                buy ? FinancialTransactionType.BUY : FinancialTransactionType.SELL,
                product);

        financialTransactionRepository.save(transaction);
        uploadLogRepository.save(new UploadLog(filename, LocalDate.now()));

        return UploadResultDto
                .builder()
                .result(UploadResult.SUCCESS)
                .type(buy ? UploadType.BUY : UploadType.SELL)
                .filename(filename)
                .isin(isin)
                .amount(amount)
                .date(date)
                .build();
    }

    private UploadResultDto processTransaction(String filename, List<String> lines) {
        boolean buy = lines.stream().anyMatch(s -> s.contains("Kauf"));
        LocalDate date = null;
        BigDecimal amount = null;
        BigDecimal value = null;
        BigDecimal quantity = null;
        String isin = null;
        Pattern isinPattern = Pattern.compile("\\((.{12})/");
        for (String l : lines) {
            if (l.startsWith("Valuta ")) {
                Matcher matcher = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}").matcher(l);

                if (matcher.find()) {
                    date = LocalDate.parse(matcher.group(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }
            if (l.contains("Endbetrag") && l.contains("EUR")) {
                Matcher matcher = Pattern.compile("-?\\d*\\.?\\d+,\\d+").matcher(l);

                if (matcher.find()) {
                    amount = new BigDecimal(matcher.group().replace(".", "").replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }
            if (l.contains("Kurswert") && l.contains("EUR")) {
                Matcher matcher = Pattern.compile("\\d*\\.?\\d+,\\d+").matcher(l.substring(l.indexOf("Kurswert")));

                if (matcher.find()) {
                    value = new BigDecimal(matcher.group().replace(".", "").replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }
            if (l.startsWith("Ordervolumen")) {
                Matcher matcher = Pattern.compile("\\d+,\\d+").matcher(l);

                if (matcher.find()) {
                    quantity = new BigDecimal(matcher.group().replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            }

            Matcher isinMatcher = isinPattern.matcher(l);

            if (isinMatcher.find()) {
                isin = isinMatcher.group(1);
            }
        }

        FinancialProduct product = getOrCreateFinancialProduct(isin);
        FinancialTransaction transaction = new FinancialTransaction(date,
                value,
                quantity,
                buy ? Objects.requireNonNull(amount).abs().subtract(value) : Objects.requireNonNull(value).subtract(amount),
                buy ? FinancialTransactionType.BUY : FinancialTransactionType.SELL,
                product);

        financialTransactionRepository.save(transaction);
        uploadLogRepository.save(new UploadLog(filename, LocalDate.now()));

        return UploadResultDto
                .builder()
                .result(UploadResult.SUCCESS)
                .type(buy ? UploadType.BUY : UploadType.SELL)
                .filename(filename)
                .isin(isin)
                .amount(amount)
                .date(date)
                .build();
    }

    private UploadResultDto processDividend(String filename, List<String> lines) {
        LocalDate date = null;
        BigDecimal amount = null;
        String isin = null;
        Pattern isinPattern = Pattern.compile("\\((.{12})/");
        for (String l : lines) {
            if (l.startsWith("Valuta ")) {
                Matcher matcher = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}").matcher(l);

                if (matcher.find()) {
                    date = LocalDate.parse(matcher.group(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            } else if (l.trim().startsWith("Endbetrag")) {
                Matcher matcher = Pattern.compile("-?\\d*\\.?\\d+,\\d+").matcher(l);

                if (matcher.find()) {
                    amount = new BigDecimal(matcher.group().replace(".", "").replace(",", "."));
                } else {
                    throw new RuntimeException("File cannot be parsed");
                }
            } else {
                Matcher isinMatcher = isinPattern.matcher(l);

                if (isinMatcher.find()) {
                    isin = isinMatcher.group(1);
                }
            }
        }

        FinancialProduct product = getOrCreateFinancialProduct(isin);
        dividendRepository.save(new Dividend(date, amount, product));
        uploadLogRepository.save(new UploadLog(filename, LocalDate.now()));

        return UploadResultDto
                .builder()
                .result(UploadResult.SUCCESS)
                .type(UploadType.DIVIDEND)
                .filename(filename)
                .isin(isin)
                .amount(amount)
                .date(date)
                .build();
    }

    private FinancialProduct getOrCreateFinancialProduct(String isin) {
        return productRepository.findById(isin).orElseGet(() ->
                //TODO add notification for new product
                productRepository.save(new FinancialProduct(isin))
        );
    }
}
