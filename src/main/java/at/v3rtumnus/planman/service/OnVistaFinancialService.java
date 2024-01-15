package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnVistaFinancialService {

    public static final String USD_EUR_URL = "https://www.onvista.de/devisen/Dollarkurs-USD-EUR";

    public StockInfo getStockInfo(String url) {
        try {
            Document doc = Jsoup.connect(url).get();

            List<TextNode> valueList = doc.selectXpath("//data[@value]/text()", TextNode.class);

            BigDecimal marketPrice = new BigDecimal(valueList.get(0).text().replace(",", "."));
            BigDecimal changeToday = new BigDecimal(valueList.get(1).text().replace(",", "."));
            BigDecimal changePercent = new BigDecimal(valueList.get(2).text().replace(",", "."));

            List<TextNode> currencyList = doc.selectXpath("//data[@value]/span/text()", TextNode.class);

            return new StockInfo(marketPrice,
                    changeToday,
                    changePercent,
                    currencyList.get(1).text());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public BigDecimal getUSDExchangeRate() {
        try {
            Document doc = Jsoup.connect(USD_EUR_URL).get();

            List<TextNode> valueList = doc.selectXpath("//data[@value]/text()", TextNode.class);

            return new BigDecimal(valueList.get(0).text().replace(",", "."));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
