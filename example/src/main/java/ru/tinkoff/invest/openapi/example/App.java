package ru.tinkoff.invest.openapi.example;

import com.workday.insights.matrix.Forecast;
import oracle.jdbc.internal.OracleTypes;
import oracle.sql.ARRAY;
import oracle.sql.Datum;
import oracle.sql.StructDescriptor;
import ru.tinkoff.invest.openapi.SimpleStopLossStrategy;
import ru.tinkoff.invest.openapi.StrategyExecutor;
import ru.tinkoff.invest.openapi.data.*;
import ru.tinkoff.invest.openapi.wrapper.Connection;
import ru.tinkoff.invest.openapi.wrapper.Context;
import ru.tinkoff.invest.openapi.wrapper.SandboxContext;
import ru.tinkoff.invest.openapi.wrapper.impl.ConnectionFactory;
import ru.tinkoff.invest.openapi.wrapper.impl.SandboxContextImpl;


import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;
import java.util.Properties;

//import java.sql.PreparedStatement;
//import java.sql.SQLException;

public class App {
    private static String ssoToken;
    private static String ticker;
    private static CandleInterval candleInterval;
    private static BigDecimal maxVolume;
    private static boolean useSandbox;

    private static SandboxContext sandboxContext;

    static BigDecimal[] nums;
    static String[] tickers;
    static String[] figis;
    static int iAllStocks;
    static FileInputStream fis;
    static Properties property = new Properties();
    static String cUSER, cPWD, cTNS_ADMIN;

    public static void main(String[] args) {
        //db_log("main", "Strts");
        final Logger logger;
        try {
            fis = new FileInputStream("C:\\TKS\\invest\\example\\src\\main\\resources\\app_props.properties ");
            property.load(fis);
            cUSER = property.getProperty("USER");
            cPWD = property.getProperty("PWD");
            cTNS_ADMIN = property.getProperty("TNS_ADMIN");
        } catch (IOException e) {
            System.err.println("Properties file not found!");
        }


        try {
            logger = initLogger();
            extractParams(args, logger);
        } catch (IllegalArgumentException ex) {
            return;
        } catch (IOException ex) {
            System.err.println("При инициализации логгера произошла ошибка: " + ex.getLocalizedMessage());
            return;
        }
        db_log("main", "Starts",logger);
        logger.severe("Working Directory = " +
                System.getProperty("user.dir"));


        logger.severe("tns Directory = " +
                System.getProperty("oracle.net.tns_admin"));


        try {
            final Connection connection;
            final Context context;

            logger.severe("Before test_conn");
            test_conn(logger);

            if (useSandbox) {
                logger.fine("Создаём подключение в режиме \"песочницы\"... ");
                connection = ConnectionFactory.connectSandbox(ssoToken, logger).join();
            } else {
                logger.fine("Создаём подключение в биржевом режиме... ");
                connection = ConnectionFactory.connect(ssoToken, logger).join();
            }
            db_log("main"," call initCleanupProcedure",logger);
            initCleanupProcedure(connection, logger);

            context = connection.context();

            if (useSandbox) {
                // ОБЯЗАТЕЛЬНО нужно выполнить регистрацию в "песочнице"
                ((SandboxContext) context).performRegistration();
            }

            try {
                sandboxContext = new SandboxContextImpl(connection, logger);
                System.out.println(" 1 ");
                final var someFigi = "BBG000BR37X2";
                System.out.println(" 2 ");
                final var someBalance = BigDecimal.valueOf(1000);
                System.out.println(" 3 ");
                final var resultSandboxContext = sandboxContext.setPositionBalance(someFigi, someBalance).get();
                db_log("main"," sandboxContext.setPositionBalance ",logger);
                System.out.println(" 4 ");
                System.out.println(" resultSandboxContext " + resultSandboxContext.toString());
            } catch (Exception ex) {
                System.out.println(" error in setPositionBalance " + ex.toString());
            }
            final var PortfolioResponse = context.getPortfolio().get();
            System.out.println(" PortfolioResponse getPositions().size() " + PortfolioResponse.getPositions().size());



            /*for (Portfolio.PortfolioPosition entity : PortfolioResponse.getPositions()) {
                System.out.println(
                        " getTicker " + entity.getTicker() +
                                " getInstrumentType " + entity.getInstrumentType() +
                                " getBalance " + entity.getBalance() +
                                " getBlocked " + entity.getBlocked() +
                                " getExpectedYield " + entity.getExpectedYield() +
                                " getLots " + entity.getLots() +
                                " getAveragePositionPrice " + entity.getAveragePositionPrice() +
                                " getAveragePositionPriceNoNkd " + entity.getAveragePositionPriceNoNkd());

            }

             */
            // get_portfolio(context,logger);


            final var resultClearAll = sandboxContext.clearAll().get();
            db_log("main","sandboxContext.clearAll",logger);

            int forecastSize = 180;
            final InstrumentsList marketStocksResponse = context.getMarketStocks().get();
            db_log("main","after getMarketStocks",logger);
            //final var marketStocksResponse = context.getMarketStocks().get();
            logger.severe("Before saveMarketStocks");
            db_log("main","before saveMarketStocks",logger);
            saveMarketStocks(marketStocksResponse, logger);
            logger.severe("After saveMarketStocks");
            db_log("main","before get_tickers",logger);
            get_tickers(logger, "RUB");
            //save_stock_candles(context, marketStocksResponse, logger, forecastSize,tickers);
            db_log("main","before save_stock_candles",logger);
            save_stock_candles(context, logger, forecastSize, figis, tickers, iAllStocks);
            logger.severe("After save_stock_candles");


            // получим список интересующих нас инструментов


            OffsetDateTime from = OffsetDateTime.of(2017, 01, 01, 7, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime to = OffsetDateTime.of(2019, 10, 14, 7, 0, 0, 0, ZoneOffset.UTC);
            forecastSize = (int) ChronoUnit.DAYS.between(OffsetDateTime.of(2017, 8, 01, 7, 0, 0, 0, ZoneOffset.UTC), to);
            to = to.minusDays(forecastSize);

            //String sTicker_forecast;
            /*for (int j=0;j<4;j++) {
                forecast(tickers[j],logger,from,to,forecastSize);
                /*logger.severe(" forecastData.length " + forecastData.length);
                for (int i = 0; i < forecastData.length; i++) {
                    logger.severe("i " + i + " forecastData " + forecastData[i]);
                }
                }
                logger.severe("after Forecast");
            */
            ///////////////////////////////////////////////


            logger.severe("before get_ticker_for_trade ticker " + ticker);
            if (ticker.equals("---")) {
                try {
                    db_log("main","before get_ticker_for_trade ",logger);
                    ticker = get_ticker_for_trade("RUB");
                    logger.severe("after get_ticker_for_trade ticker " + ticker);
                } catch (Exception ex) {
                    logger.severe("error in get_ticker_for_trade " + ex.toString());
                }
            }

            logger.fine("Ищём по тикеру " + ticker + "... ");
            final var instrumentsList = context.searchMarketInstrumentsByTicker(ticker).join();

            final var instrumentOpt = instrumentsList.getInstruments()
                    .stream()
                    .findFirst();

            final Instrument instrument;
            if (instrumentOpt.isEmpty()) {
                logger.severe("Не нашлось инструмента с нужным тикером.");
                return;
            } else {
                instrument = instrumentOpt.get();
            }

           /* final var from = OffsetDateTime.of(2019, 11, 7, 0, 0, 0, 0, ZoneOffset.UTC);
            final var to = OffsetDateTime.of(2019, 11, 7, 23, 59, 59, 0, ZoneOffset.UTC);
            HistoricalCandles lHistoricalCandles;
            String  sFigi;
            sFigi = instrument.getFigi();
            lHistoricalCandles = context.getMarketCandles(sFigi, from, to, CandleInterval.ONE_MIN).get();
            List<Candle> lCandles;
            lCandles = lHistoricalCandles.getCandles();
*/
            //save_candles(lCandles,instrument.getTicker(),logger);


            if (useSandbox) {
                db_log("main","before initPrepareSandbox",logger);
                initPrepareSandbox((SandboxContext) context, instrument, logger);
            }

            logger.fine("Получаем валютные балансы... ");
            final var portfolioCurrencies = context.getPortfolioCurrencies().join();

            final var portfolioCurrencyOpt = portfolioCurrencies.getCurrencies().stream()
                    .filter(pc -> pc.getCurrency() == instrument.getCurrency())
                    .findFirst();

            final PortfolioCurrencies.PortfolioCurrency portfolioCurrency;
            if (portfolioCurrencyOpt.isEmpty()) {
                logger.severe("Не нашлось нужной валютной позиции.");
                return;
            } else {
                portfolioCurrency = portfolioCurrencyOpt.get();
            }

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; 1 == 1; i++) {
                    System.out.println("get_portfolio");
                    db_log("main","before get_portfolio in CompletableFuture.runAsync ",logger);
                    get_portfolio(context, logger);

                    i--;
                    try {
                        sleep(1000 * 10);
                    } catch (Exception ex) {
                    }

                }
            });

            logger.fine("Запускаем робота... ");
            final CompletableFuture<Void> result = new CompletableFuture<>();
            final var strategy = new SimpleStopLossStrategy(
                    portfolioCurrency,
                    instrument,
                    maxVolume,
                    5,
                    candleInterval,
                    BigDecimal.valueOf(0.01),
                    BigDecimal.valueOf(0.01),
                    BigDecimal.valueOf(0.01),
                    BigDecimal.valueOf(0.01),
                    logger
            );
            final var strategyExecutor = new StrategyExecutor(context, strategy, logger);
            db_log("main","before strategyExecutor.run ",logger);
            strategyExecutor.run();
            result.join();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Что-то пошло не так.", ex);
        }
    }


    private static Logger initLogger() throws IOException {
        LogManager logManager = LogManager.getLogManager();
        final var classLoader = App.class.getClassLoader();

        try (InputStream input = classLoader.getResourceAsStream("logging.properties")) {

            if (input == null) {
                throw new FileNotFoundException();
            }

            Files.createDirectories(Paths.get("./logs"));
            logManager.readConfiguration(input);
        }

        return Logger.getLogger(App.class.getName());
    }

    private static void extractParams(final String[] args, final Logger logger) throws IllegalArgumentException {
        if (args.length == 0) {
            logger.severe("Не передан авторизационный токен!");
            throw new IllegalArgumentException();
        } else if (args.length == 1) {
            logger.severe("Не передан исследуемый тикер!");
            throw new IllegalArgumentException();
        } else if (args.length == 2) {
            logger.severe("Не передан разрешающий интервал свечей!");
            throw new IllegalArgumentException();
        } else if (args.length == 3) {
            logger.severe("Не передан допустимый объём используемых средств!");
            throw new IllegalArgumentException();
        } else if (args.length == 4) {
            logger.severe("Не передан признак использования песочницы!");
            throw new IllegalArgumentException();
        } else {
            ssoToken = args[0];
            ticker = args[1];


            switch (args[2]) {
                case "1min":
                    candleInterval = CandleInterval.ONE_MIN;
                    break;
                case "2min":
                    candleInterval = CandleInterval.TWO_MIN;
                    break;
                case "3min":
                    candleInterval = CandleInterval.THREE_MIN;
                    break;
                case "5min":
                    candleInterval = CandleInterval.FIVE_MIN;
                    break;
                case "10min":
                    candleInterval = CandleInterval.TEN_MIN;
                    break;
                default:
                    logger.severe("Не распознан разрешающий интервал!");
                    throw new IllegalArgumentException();
            }
            maxVolume = new BigDecimal(args[3]);
            useSandbox = Boolean.parseBoolean(args[4]);
        }
    }

    private static void initPrepareSandbox(final SandboxContext context,
                                           final Instrument instrument,
                                           final Logger logger) {
        logger.fine("Очищаем всё позиции... ");
        context.clearAll().join();

        logger.fine("Ставим на баланс немного " + instrument.getCurrency() + "... ");
        context.setCurrencyBalance(instrument.getCurrency(), maxVolume).join();
    }

    private static void initCleanupProcedure(final Connection connection, final Logger logger) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Закрываем соединение... ");
                connection.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Что-то произошло при закрытии соединения!", e);
            }
        }));
    }

    public static void test_conn(Logger logger) throws ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);

        logger.severe("tns Directory = " +
                System.getProperty("oracle.net.tns_admin"));

        String dbURL = property.getProperty("dbURL");
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;
        Statement stmt = null;
        try {
            conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM DUAL");
            if (rs.next()) {
                logger.severe(rs.getString(1));
            }
        } catch (Exception e) {
            logger.severe(" test_conn exception " + e.toString());
            e.printStackTrace();
        } finally {
            if (stmt != null) try {
                stmt.close();
            } catch (Exception e) {
            }
            if (conn != null) try {
                conn.close();
            } catch (Exception e) {
            }
        }
    }

    public static java.sql.Connection get_conn(Logger logger) throws ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        String dbURL = property.getProperty("dbURL");
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;

        try {
            conn = DriverManager.getConnection(dbURL, cUSER, cPWD);

        } catch (Exception e) {
            logger.severe(" test_conn exception " + e.toString());
            e.printStackTrace();
        } finally {
            if (conn != null) try {
                conn.close();
            } catch (Exception e) {
            }
        }
        return conn;
    }

    public static void saveMarketStocks(InstrumentsList entities, Logger logger) throws SQLException, ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        //String sql = "INSERT INTO LLC_TKS_MS (TICKER) Values (?)";
        String sql = "MERGE INTO LLC_TKS_MS m using (select ? TICKER, ? FIGI, ? ISIN,? MINPRICEINCREMENT, ? LOT, ? CURRENCY, ? INAME from dual) d " +
                " on (d.TICKER = m.TICKER) " +
                " when matched then " +
                " update set FIGI = d.FIGI,ISIN = d.ISIN,MINPRICEINCREMENT = d.MINPRICEINCREMENT,LOT = d.LOT,CURRENCY = d.CURRENCY,INAME = d.INAME " +
                " when not matched then " +
                " insert (  TICKER,  FIGI,  ISIN,  MINPRICEINCREMENT,  LOT,   CURRENCY,   INAME)  " +
                " Values (d.TICKER,d.FIGI,d.ISIN,d.MINPRICEINCREMENT,d.LOT, d.CURRENCY, d.INAME)";


        String dbURL = property.getProperty("dbURL");
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;
        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                PreparedStatement statement = connection.prepareStatement(sql);
        ) {
            int i = 0;
            for (Instrument entity : entities.getInstruments()) {
                statement.setString(1, entity.getTicker());
                statement.setString(2, entity.getFigi());
                statement.setString(3, entity.getIsin());
                statement.setBigDecimal(4, entity.getMinPriceIncrement());
                statement.setInt(5, entity.getLot());
                statement.setString(6, entity.getCurrency().toString());
                statement.setString(7, entity.getName());
                // ...
                statement.addBatch();
                i++;
                if (i % 1000 == 0 || i == entities.getInstruments().size()) {
                    statement.executeBatch(); // Execute every 1000 items.
                }
            }
        } catch (Exception ex) {
            logger.severe(" save exception " + ex.toString());
        }
    }

    public static void save_candles(List<Candle> entities, String ticker, Long MSC_H_ID, Logger logger) throws SQLException, ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        String sql = "INSERT INTO LLC_TKS_MSC_B (TICKER,FIGI,CANDLE_INTERVAL,PRICE_O,PRICE_C,PRICE_H,PRICE_L,DEALS_VOLUME,CANDLE_TIME,MSC_H_ID) Values (?,?,?,?,?,?,?,?,?,?)";


        //String dbURL = property.getProperty("dbURL_TEST");
        String dbURL = property.getProperty("dbURL");
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;
        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                PreparedStatement statement = connection.prepareStatement(sql);
        ) {
            int i = 0;
            for (Candle entity : entities) {


                statement.setString(1, ticker);
                statement.setString(2, entity.getFigi());
                statement.setString(3, "" + entity.getInterval());
                statement.setBigDecimal(4, entity.getO());
                statement.setBigDecimal(5, entity.getC());
                statement.setBigDecimal(6, entity.getH());
                statement.setBigDecimal(7, entity.getL());
                statement.setBigDecimal(8, entity.getV());
                statement.setString(9, "" + entity.getTime());
                statement.setLong(10, MSC_H_ID);

                // ...
                statement.addBatch();
                i++;
                if (i % 1000 == 0 || i == entities.size()) {
                    statement.executeBatch(); // Execute every 1000 items.
                }
            }
        } catch (Exception ex) {
            logger.severe(" save exception " + ex.toString());
        }
    }

    //public static void save_stock_candles(Context context, InstrumentsList instrumentsList, Logger logger, int forecastSize, String [] ltickers)
    public static void save_stock_candles(Context context, Logger logger, int forecastSize, String[] lFigis, String[] lTickers, int lAllStocks) {
        db_log("save_stock_candles" , "starts lAllStocks " + lAllStocks, logger);
        //Long nId;
        String sFigi, sHCS;
        String sTicker;
        //Instrument vInstrument;
        //List<Candle> lCandles;
        final var from = OffsetDateTime.of(2017, 01, 01, 7, 0, 0, 0, ZoneOffset.UTC);
        //final var to = OffsetDateTime.of(2019, 11, 15, 23, 59, 59, 999, ZoneOffset.UTC);
        final var to = OffsetDateTime.now();

        //HistoricalCandles lHistoricalCandles;
        OffsetDateTime cycl_from;
        OffsetDateTime cycl_to;
        for (int i = 0; i < lAllStocks; i++) {
            try {
                //vInstrument = instrumentsList.getInstruments().get(i);
                sFigi = lFigis[i];
                sTicker = lTickers[i];
                logger.severe("sTicker  " + sTicker + " sFigi  " + sFigi + " i " + i);

                get_orders(context, logger, sFigi);

        for (cycl_from = from; cycl_from.compareTo(to) < 0; cycl_from = cycl_from.plusYears(1).truncatedTo(ChronoUnit.DAYS).withDayOfYear(1)) {
            if (cycl_from.plusYears(1).minusSeconds(1).compareTo(to) < 0) {
                cycl_to = cycl_from.plusYears(1).truncatedTo(ChronoUnit.DAYS).withDayOfYear(1).minusSeconds(1);
            } else {
                cycl_to = to;
            }
            logger.severe("in cycle cycl_from  " + cycl_from.toString() + " cycl_to " + cycl_to.toString());
            db_log("save_stock_candles", "524 in cycle cycl_from  " + cycl_from.toString() + " cycl_to " + cycl_to.toString(),logger);
            Integer j = 0;
            try {

                        //sTicker = vInstrument.getTicker();
                        //logger.severe("Test sTicker  "+  sTicker);

                        /*for (int dd = 1; dd <= 1; dd++) {

                            final var MarketOrderbookResponse = context.getMarketOrderbook(sFigi, dd).get();
                            logger.severe(" depth " + dd + " MarketOrderbookResponse.getTradeStatus()  " + MarketOrderbookResponse.getTradeStatus() +
                                    " MarketOrderbookResponse.getTradeStatus() " + MarketOrderbookResponse.getTradeStatus() +
                                    " MarketOrderbookResponse.getMinPriceIncrement() " + MarketOrderbookResponse.getMinPriceIncrement() +
                                    " MarketOrderbookResponse.getLastPrice() " + MarketOrderbookResponse.getLastPrice() +
                                    " MarketOrderbookResponse.getClosePrice() " + MarketOrderbookResponse.getClosePrice() +
                                    " MarketOrderbookResponse.getLimitUp() " + MarketOrderbookResponse.getLimitUp() +
                                    " MarketOrderbookResponse.getLimitDown() " + MarketOrderbookResponse.getLimitDown()
                            );
                            for (Orderbook.OrderbookItem bid : MarketOrderbookResponse.getBids()) {
                                System.out.println(" bid bid.getPrice() " + bid.getPrice() + " bid.getQuantity() " + bid.getQuantity());
                            }
                            for (Orderbook.OrderbookItem ask : MarketOrderbookResponse.getAsks()) {
                                System.out.println(" ask ask.getPrice() " + ask.getPrice() + " ask.getQuantity() " + ask.getQuantity());
                            }

                        }

                         */


                        if (j > 49) {
                            sleep(1000 * 61);
                            j = 0;
                        }
                        j++;

                        OffsetDateTime cycl_from2 = get_from_date_by_ticker(sTicker, cycl_from , cycl_to, logger);

                        //db_log("save_stock_candles", "befor get_candles sTicker " + sTicker + " cycl_from " + cycl_from.toString() + " cycl_to "+cycl_to.toString(), logger);
                        //StringWriter sw = new StringWriter();
                        //PrintWriter pw = new PrintWriter(sw);
                        //e.printStackTrace(pw);
                        //String sStackTrace = sw.toString(); // stack trace as a string
                        //System.out.println(sStackTrace);
                        db_log("save_stock_candles", " 574 befor get_candles sTicker " + sTicker + " cycl_from " + cycl_from.toString() + " cycl_to "+cycl_to.toString(), logger);
                        get_candles(context, sFigi, cycl_from2, cycl_to, sTicker, logger, CandleInterval.DAY);
                            /*lHistoricalCandles = context.getMarketCandles(sFigi, cycl_from, cycl_to, CandleInterval.DAY).get();
                            //lHistoricalCandles = null;

                            nId = save_candles_search_params(sFigi, sTicker, cycl_from, cycl_to, CandleInterval.DAY, logger);

                            lCandles = lHistoricalCandles.getCandles();
                            save_candles(lCandles, sTicker, nId, logger);

                             */
                            /*
                            if (cycl_to == to) {
                                String lTicker = sTicker;

                                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                    System.out.println("Hi forecast lTicker " + lTicker + " from " + from + " to " + to);
                                    forecast(lTicker, logger, from, to, forecastSize);
                                });

                            }
                            */


                    } catch (Exception ex) {
                        logger.severe("Exception in candle value  " + ex.toString() + " i " + i);
                        db_log("save_stock_candles"," exception 1 " + ex.toString() ,logger);
                    }

                }
            } catch (Exception ex) {
                logger.severe("Exception in candles " + ex.toString());
                db_log("save_stock_candles ", "exception 2 " + ex.toString() ,logger);
            }
            ;
        }
    }

    public static Long save_candles_search_params(String figi,
                                                  String ticker,
                                                  OffsetDateTime from,
                                                  OffsetDateTime to,
                                                  CandleInterval interval,
                                                  Logger logger) throws SQLException, ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        String sql = "BEGIN INSERT INTO LLC_TKS_MSC_H (TICKER,FIGI,CANDLE_INTERVAL,FROM_DATE,TILL_DATE) Values (?,?,?,?,?) returning REC_ID into ?; END;";

        String dbURL = property.getProperty("dbURL");

        Class.forName("oracle.jdbc.OracleDriver");

        //java.sql.Connection conn = null;
        Long lRec_id;
        try (
                //java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                java.sql.Connection connection = DriverManager.getConnection(dbURL, cUSER, cPWD);
                CallableStatement statement = connection.prepareCall(sql);
        ) {
            statement.setString(1, ticker);

            statement.setString(2, figi);

            statement.setString(3, "" + interval);
            statement.setString(4, "" + from);
            statement.setString(5, "" + to);
            statement.registerOutParameter(6, OracleTypes.NUMBER);
            // ...
            logger.severe(" save_candles_search_params before execute ");
            statement.execute();
            lRec_id = statement.getLong(6);
        } catch (Exception ex) {
            logger.severe(" save_candles_search_params exception " + ex.toString());
            return null;
        }
        return lRec_id;
    }

    //////////////////////////////////////////////////////////////////////////////
    public static void get_rates_by_ticker(String ticker,
                                           OffsetDateTime from,
                                           OffsetDateTime till,
                                           Logger logger) throws SQLException, ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        String sql = "BEGIN LLC_TKS_MSC_UTILS.GET_RATES_BY_TICKET(?,?,?,?,?); END;";

        //String dbURL = "jdbc:oracle:thin:@RBR";
        String dbURL = property.getProperty("dbURL");
        logger.severe(" get_rates_by_ticker ticker " + ticker);
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;

        String sErr_Msg;
        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                CallableStatement statement = connection.prepareCall(sql);
        ) {
            statement.registerOutParameter(1, OracleTypes.VARCHAR);
            statement.setString(2, ticker);
            statement.setString(3, "" + from);
            statement.setString(4, "" + till);
            statement.registerOutParameter(5, Types.ARRAY, "LLC_NUMBER_NTT");
            // ...
            logger.severe(" get_rates_by_ticker before execute ");
            statement.execute();
            sErr_Msg = statement.getString(1);
            nums = (BigDecimal[]) (statement.getArray(5).getArray());
            System.out.println(Arrays.toString(nums));
            //logger.severe(" sErr_Msg " + sErr_Msg + " nums "+Arrays.toString(nums));
        } catch (Exception ex) {
            logger.severe(" save_candles_search_params exception " + ex.toString());

        }

    }

    //////////////////////////////////////////////////////////////////////////////
    public static void get_tickers(Logger logger, String cur) throws SQLException, ClassNotFoundException {
        logger.severe("get_ticker starts cur " + cur);
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        //String sql = "SELECT TICKER,FIGI, count(*) over () CC FROM LLC_TKS_MS WHERE SW=1 and ticker in ('PGR','AAPL')";
        //String sql = "SELECT TICKER,FIGI, count(*) over () CC FROM LLC_TKS_MS where currency = '?' and rownum < 15";
        //String sql = "SELECT TICKER,FIGI, count(*) over () CC FROM LLC_TKS_MS where rownum < 3";
        //String sql = "SELECT TICKER,FIGI, count(*) over () CC FROM LLC_TKS_MS where ticker in ('AGN')";
        //String sql = "SELECT TICKER,FIGI, count(*) over () CC FROM LLC_TKS_MS";
        String sql = property.getProperty("GET_TICKERS_QUERY");
        String vp;
        vp = "668";
        //String dbURL = "jdbc:oracle:thin:@RBR";
        String dbURL = property.getProperty("dbURL");
        vp = "671";
        Class.forName("oracle.jdbc.OracleDriver");
        //java.sql.Connection conn = null;
        vp = "674";
        try (
                java.sql.Connection connection = DriverManager.getConnection(dbURL, cUSER, cPWD);
                //CallableStatement  statement = connection.prepareCall(sql);
                PreparedStatement statement = connection.prepareStatement(sql);

        ) {
            vp = "680";
            // error statement.setString(4,cur);
            vp = "682";
            ResultSet rs = statement.executeQuery(sql);
            vp = "684";
            //int aSize = rs.getInt("CC");
            int aSize = 1500;
            System.out.println("aSize " + aSize);

            tickers = new String[aSize];
            figis = new String[aSize];
            System.out.println(1);
            int i = 0;
            while (rs.next()) {
                vp = "694";
                String coffeeName = rs.getString("TICKER");
                vp = "696";
                System.out.println("i " + i + " TICKER " + coffeeName);
                vp = "698";
                tickers[i] = coffeeName;
                figis[i] = rs.getString("FIGI");
                i++;
            }
            iAllStocks = i;
            /*while (rs.next()) {
                Array z = rs.getArray("TICKER");
                //String[] zips = (String[])z.getArray();
                tickers = (String[])z.getArray();
                for (int i = 0; i < tickers.length; i++) {
                    System.out.println(tickers[i]);
                }
            }*/
            logger.severe(" get_tickers ends  iAllStocks " + iAllStocks);
        } catch (Exception ex) {
            logger.severe(" get_tickers exception " + ex.toString() + " vp " + vp);

        }
        System.out.println("10");

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    public static void save_forecast_candles(BigDecimal[] entities,
                                             String ticker,
                                             OffsetDateTime from,
                                             OffsetDateTime to,
                                             Logger logger,
                                             int forecast_size) throws SQLException, ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);


        String dbURL = property.getProperty("dbURL");
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;
        long FC_H_ID;
        FC_H_ID = 0;
        logger.severe("save_forecast_candles starts from " + from + " to " + to);
        String sql = "BEGIN INSERT INTO LLC_TKS_FC_H (TICKER,FROM_DATE,TILL_DATE,FORECAST_SIZE) Values (?,?,?,?) returning REC_ID into ?; END;";
        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                CallableStatement statement = connection.prepareCall(sql);
        ) {


            statement.setString(1, ticker);
            statement.setString(2, "" + from);
            statement.setString(3, "" + to);
            statement.setInt(4, forecast_size);

            statement.registerOutParameter(5, OracleTypes.NUMBER);
            // ...
            logger.severe(" save_forecast_candles before execute ");
            statement.execute();
            FC_H_ID = statement.getLong(5);
            logger.severe(" save_forecast_candles after execute ");

        } catch (Exception ex) {
            logger.severe(" save exception " + ex.toString());
        }


        sql = "INSERT INTO LLC_TKS_FC_B (TICKER,PRICE_F,CANDLE_TIME,FC_H_ID) Values (?,?,?,?)";

        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                PreparedStatement statement = connection.prepareStatement(sql);
        ) {
            int i = 0;
            for (BigDecimal entity : entities) {

                statement.setString(1, ticker);
                statement.setBigDecimal(2, entity);
                statement.setString(3, "" + to.plusDays(i + 1));
                statement.setLong(4, FC_H_ID);

                statement.addBatch();
                i++;
                if (i % 1000 == 0 || i == entities.length) {
                    statement.executeBatch(); // Execute every 1000 items.
                }
            }
        } catch (Exception ex) {
            logger.severe(" save exception " + ex.toString());
        }
    }

    public static void forecast(String sTicker_forecast, Logger logger, OffsetDateTime from, OffsetDateTime to, int forecastSize) {

        logger.severe("Before get_rates_by_ticker sTicker_forecast " + sTicker_forecast + " from " + from + " to " + " forecastSize " + forecastSize);
        try {
            get_rates_by_ticker(sTicker_forecast,
                    from,
                    to,
                    logger);
        } catch (Exception ex) {
            logger.severe("Exception  get_rates_by_ticker " + ex.toString());
        }
        logger.severe("After get_rates_by_ticker");
        /////////////////////////////////////////////////
        logger.severe("Before Forecast");
        Forecast forecast = new Forecast();
        String[] arglist = new String[0];

        double[] ratesArr = new double[nums.length];

        for (int i = 0; i < nums.length; i++) {
            ratesArr[i] = nums[i].doubleValue();
        }
        logger.severe("before forecastData ratesArr.length " + ratesArr.length);
        if (ratesArr.length != 0) {
            forecast.setRatesArray(ratesArr);
            forecast.setForecastSize(forecastSize);
            double[] forecastData = forecast.main(arglist);

            BigDecimal[] bdForecast = new BigDecimal[forecastData.length];
            for (int i = 0; i < forecastData.length; i++) {
                bdForecast[i] = BigDecimal.valueOf(forecastData[i]);
                bdForecast[i] = bdForecast[i].setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            try {
                save_forecast_candles(bdForecast, sTicker_forecast, from, to, logger, forecastSize);
            } catch (Exception ex) {
                logger.severe("Exception in save_forecast_candles " + ex.toString());
            }
        }
    }

    // получение даты с которой нет свечей из запрашиваемого диапозона
    public static OffsetDateTime get_from_date_by_ticker(String ticker,
                                                         OffsetDateTime from,
                                                         OffsetDateTime till,
                                                         Logger logger) throws SQLException, ClassNotFoundException {
        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        String sql = "BEGIN LLC_TKS_MSC_UTILS.GET_FROM_DATE_BY_TICKER(?,?,?,?,?,?,?,?,?); END;";

        //String dbURL = "jdbc:oracle:thin:@RBR";
        String dbURL = property.getProperty("dbURL");
        logger.severe(" get_from_date_by_ticker ticker " + ticker + " from " + from + " till " + till);
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;
        int iRRRR, iMM, iDD, iHH24, iMI, iSS;
        iRRRR = from.getYear();
        iMM = from.getMonthValue();
        iDD = from.getDayOfMonth();
        iHH24 = from.getHour();
        iMI = from.getMinute();
        iSS = from.getSecond();
        String sErr_Msg;
        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                CallableStatement statement = connection.prepareCall(sql);
        ) {
            statement.setString(1, ticker);
            statement.setString(2, "" + from);
            statement.setString(3, "" + till);
            statement.registerOutParameter(4, OracleTypes.NUMBER);
            statement.registerOutParameter(5, OracleTypes.NUMBER);
            statement.registerOutParameter(6, OracleTypes.NUMBER);
            statement.registerOutParameter(7, OracleTypes.NUMBER);
            statement.registerOutParameter(8, OracleTypes.NUMBER);
            statement.registerOutParameter(9, OracleTypes.NUMBER);
            statement.execute();
            iRRRR = statement.getInt(4);
            iMM = statement.getInt(5);
            iDD = statement.getInt(6);
            iHH24 = statement.getInt(7);
            iMI = statement.getInt(8);
            iSS = statement.getInt(9);
            //logger.severe(" sErr_Msg " + sErr_Msg + " nums "+Arrays.toString(nums));
        } catch (Exception ex) {
            logger.severe(" get_from_date_by_ticker exception " + ex.toString());

        }
        logger.severe(" get_from_date_by_ticker RETURN " + "  " + iRRRR + "  " + iMM + "  " + iDD + "  " + iHH24 + "  " + iMI + "  " + iSS);
        return OffsetDateTime.of(iRRRR, iMM, iDD, iHH24, iMI, iSS, 0, ZoneOffset.UTC);
    }


    //////////////////////////////////////////////////////////////////////
    public static void get_portfolio(Context context, Logger logger) {

        try {
            final var PortfolioResponse = context.getPortfolio().get();
            System.out.println(" PortfolioResponse getPositions().size() " + PortfolioResponse.getPositions().size());
            int ppp = 0;
            String vp;
            for (Portfolio.PortfolioPosition entity : PortfolioResponse.getPositions()) {
                ppp++;

                System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
                String sql = "BEGIN INSERT INTO LLC_TKS_MSC_PORTFOLIO (FIGI, TICKER, ISIN,INSTRUMENT_TYPE,BALANCE,BLOCKED,EY_CURRENCY,EY_VALUE,LOTS,APP_CURRENCY,APP_VALUE,APPNN_CURRENCY,APPNN_VALUE,PNOW,PP) Values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?); END;";

                String dbURL = property.getProperty("dbURL");

                Class.forName("oracle.jdbc.OracleDriver");

                //java.sql.Connection conn = null;
                Long lRec_id;
                vp = "";
                try (
                        //java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                        java.sql.Connection connection = DriverManager.getConnection(dbURL, cUSER, cPWD);
                        CallableStatement statement = connection.prepareCall(sql);
                ) {
                    vp = "1";
                    statement.setString(1, entity.getFigi());
                    vp = "2";
                    statement.setString(2, entity.getTicker());
                    vp = "3";
                    statement.setString(3, entity.getIsin());
                    vp = "4";
                    statement.setString(4, entity.getInstrumentType().toString());
                    vp = "5";
                    statement.setBigDecimal(5, entity.getBalance());
                    vp = "6";
                    statement.setBigDecimal(6, entity.getBlocked());
                    vp = "7";
                    String yec = "";
                    BigDecimal yev = BigDecimal.ZERO;
                    try {
                        yec = entity.getExpectedYield().getCurrency().toString();
                        yev = entity.getExpectedYield().getValue();
                    } catch (Exception ex) {
                    }
                    ;
                    statement.setString(7, yec);
                    vp = "8";
                    statement.setBigDecimal(8, yev);
                    vp = "9";
                    statement.setInt(9, entity.getLots());
                    vp = "10";
                    String appc = "";
                    BigDecimal appv = BigDecimal.ZERO;
                    try {
                        appc = entity.getAveragePositionPrice().getCurrency().toString();
                        appv = entity.getAveragePositionPrice().getValue();
                    } catch (Exception ex) {
                    }
                    ;

                    statement.setString(10, appc);
                    vp = "11";
                    statement.setBigDecimal(11, appv);
                    vp = "12";
                    String appnnc = "";
                    BigDecimal appnnv = BigDecimal.ZERO;
                    try {
                        appnnc = entity.getAveragePositionPriceNoNkd().getCurrency().toString();
                        appnnv = entity.getAveragePositionPriceNoNkd().getValue();
                    } catch (Exception ex) {
                    }
                    ;
                    statement.setString(12, appnnc);
                    vp = "13";
                    statement.setBigDecimal(13, appnnv);
                    vp = "14";
                    statement.setString(14, OffsetDateTime.now().toString());
                    vp = "15";
                    statement.setString(15, ppp + " / " + PortfolioResponse.getPositions().size());
                    // ...
                    logger.severe(" LLC_TKS_MSC_PORTFOLIO before execute ");
                    statement.execute();
                } catch (Exception ex) {
                    logger.severe(" LLC_TKS_MSC_PORTFOLIO execute sql exception " + ex.toString() + " vp " + vp);
                }


                logger.severe(" get_from_date_by_ticker RETURN  getTicker " + entity.getTicker() +
                        " Figi " + entity.getFigi() +
                        " InstrumentType " + entity.getInstrumentType() +
                        " Balance " + entity.getBalance() +
                        " Blocked " + entity.getBlocked() +
                        " ExpectedYield " + entity.getExpectedYield() +
                        " Lots " + entity.getLots() +
                        " AveragePositionPrice " + entity.getAveragePositionPrice() +
                        " AveragePositionPriceNoNkd " + entity.getAveragePositionPriceNoNkd());
                System.out.println(
                        " Ticker " + entity.getTicker() +
                                " InstrumentType " + entity.getInstrumentType() +
                                " Balance " + entity.getBalance() +
                                " Blocked " + entity.getBlocked() +
                                " ExpectedYield " + entity.getExpectedYield() +
                                " Lots " + entity.getLots() +
                                " AveragePositionPrice " + entity.getAveragePositionPrice() +
                                " AveragePositionPriceNoNkd " + entity.getAveragePositionPriceNoNkd());
                get_orders(context, logger, entity.getFigi());
                get_candles(context, entity.getFigi(), OffsetDateTime.now().minusMinutes(10), OffsetDateTime.now(), entity.getTicker(), logger, CandleInterval.ONE_MIN);
            }

        } catch (Exception ex) {
            System.out.println(" Error in get_portfolio " + ex.toString());
            logger.severe(" LLC_TKS_MSC_PORTFOLIO  exception " + ex.toString());
        }

        try {
            final var PortfolioCurrencyResponse = context.getPortfolioCurrencies().get();
            System.out.println(" PortfolioResponse getPositions().size() " + PortfolioCurrencyResponse.getCurrencies().size());
            int ppp = 0;
            String vp;
            for (PortfolioCurrencies.PortfolioCurrency entity : PortfolioCurrencyResponse.getCurrencies()) {
                ppp++;

                System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
                String sql = "BEGIN INSERT INTO LLC_TKS_MSC_PORTFOLIO_CUR (BLOCKED,CURRENCY,CVALUE,PNOW,PP) Values (?,?,?,?,?); END;";

                String dbURL = property.getProperty("dbURL");

                Class.forName("oracle.jdbc.OracleDriver");

                //java.sql.Connection conn = null;
                Long lRec_id;
                vp = "";
                try (
                        //java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                        java.sql.Connection connection = DriverManager.getConnection(dbURL, cUSER, cPWD);
                        CallableStatement statement = connection.prepareCall(sql);
                ) {
                    vp = "1";
                    statement.setBigDecimal(1, entity.getBlocked());
                    vp = "2";
                    statement.setString(2, entity.getCurrency().toString());
                    vp = "3";
                    statement.setBigDecimal(3, entity.getBalance());
                    vp = "4";
                    vp = "14";
                    statement.setString(4, OffsetDateTime.now().toString());
                    vp = "15";
                    statement.setString(5, ppp + " / " + PortfolioCurrencyResponse.getCurrencies().size());
                    // ...
                    logger.severe(" LLC_TKS_MSC_PORTFOLIO_CUR before execute ");
                    statement.execute();
                } catch (Exception ex) {
                    logger.severe(" LLC_TKS_MSC_PORTFOLIO_CUR execute sql exception " + ex.toString() + " vp " + vp);
                }

            }

        } catch (Exception ex) {
            System.out.println(" Error in get_portfolio " + ex.toString());
            logger.severe(" LLC_TKS_MSC_PORTFOLIO  exception " + ex.toString());
        }


    }

    public static void get_orders(Context context, Logger logger, String sFigi) {
        try {
            for (int dd = 1; dd <= 2; dd++) {

                final var MarketOrderbookResponse = context.getMarketOrderbook(sFigi, dd).get();

                System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
                String dbURL = property.getProperty("dbURL");
                Class.forName("oracle.jdbc.OracleDriver");
                java.sql.Connection conn = null;
                long O_REC_ID;
                O_REC_ID = 0;
                logger.severe("get_orders sql ");
                String sql = "BEGIN INSERT INTO LLC_TKS_MSC_ORDERBOOK (DEPTH,BIDS,ASKS,FIGI,TRADESTATUS,MINPRICEINCREMENT,LASTPRICE,CLOSEPRICE,LIMITUP,LIMITDOWN,ONOW) Values (?,?,?,?,?,?,?,?,?,?,?) returning REC_ID into ?; END;";
                try (
                        java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                        CallableStatement statement = connection.prepareCall(sql);
                ) {
                    statement.setInt(1, MarketOrderbookResponse.getDepth());
                    statement.setInt(2, MarketOrderbookResponse.getBids().size());
                    statement.setInt(3, MarketOrderbookResponse.getAsks().size());
                    statement.setString(4, MarketOrderbookResponse.getFigi());
                    statement.setString(5, MarketOrderbookResponse.getTradeStatus().toString());
                    statement.setBigDecimal(6, MarketOrderbookResponse.getMinPriceIncrement());
                    statement.setBigDecimal(7, MarketOrderbookResponse.getLastPrice());
                    statement.setBigDecimal(8, MarketOrderbookResponse.getClosePrice());
                    statement.setBigDecimal(9, MarketOrderbookResponse.getLimitUp());
                    statement.setBigDecimal(10, MarketOrderbookResponse.getLimitDown());
                    statement.setString(11, "" + OffsetDateTime.now());
                    statement.registerOutParameter(12, OracleTypes.NUMBER);
                    logger.severe(" get_orders before execute ");
                    statement.execute();
                    O_REC_ID = statement.getLong(12);
                    logger.severe(" get_orders after execute O_REC_ID " + O_REC_ID);

                } catch (Exception ex) {
                    logger.severe(" save exception " + ex.toString());
                }

                for (Orderbook.OrderbookItem bid : MarketOrderbookResponse.getBids()) {
                    save_bid_asks("B", O_REC_ID, bid, logger, MarketOrderbookResponse.getBids().size());
                }

                logger.severe(" depth " + dd + " TradeStatus()  " + MarketOrderbookResponse.getTradeStatus() +
                        " TradeStatus() " + MarketOrderbookResponse.getTradeStatus() +
                        " MinPriceIncrement() " + MarketOrderbookResponse.getMinPriceIncrement() +
                        " LastPrice() " + MarketOrderbookResponse.getLastPrice() +
                        " ClosePrice() " + MarketOrderbookResponse.getClosePrice() +
                        " LimitUp() " + MarketOrderbookResponse.getLimitUp() +
                        " LimitDown() " + MarketOrderbookResponse.getLimitDown()
                );

                for (Orderbook.OrderbookItem ask : MarketOrderbookResponse.getAsks()) {
                    System.out.println(" ask ask.getPrice() " + ask.getPrice() + " ask.getQuantity() " + ask.getQuantity());
                    save_bid_asks("A", O_REC_ID, ask, logger, MarketOrderbookResponse.getAsks().size());
                }

            }
        } catch (Exception ex) {
        }
    }
    //////////////////////////////////////////////////////////////////////////////


    public static void save_bid_asks(String O_TYPE, Long O_REC_ID, Orderbook.OrderbookItem bid, Logger logger, int O_SIZE) {
        try {
            System.out.println(" bid bid.getPrice() " + bid.getPrice() + " bid.getQuantity() " + bid.getQuantity());
            System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
            String dbURL = property.getProperty("dbURL");
            Class.forName("oracle.jdbc.OracleDriver");
            java.sql.Connection conn = null;
            String sql;
            sql = "INSERT INTO LLC_TKS_MSC_ORDERBOOKITEM (O_REC_ID,O_TYPE,PRICE,QUANTITY) Values (?,?,?,?)";

            try (
                    java.sql.Connection connection = DriverManager.getConnection(dbURL, cUSER, cPWD);
                    PreparedStatement statement = connection.prepareStatement(sql);
            ) {
                int i = 0;


                statement.setLong(1, O_REC_ID);
                statement.setString(2, O_TYPE);
                statement.setBigDecimal(3, bid.getPrice());
                statement.setBigDecimal(4, bid.getQuantity());

                statement.addBatch();
                i++;
                if (i % 1000 == 0 || i == O_SIZE) {
                    statement.executeBatch(); // Execute every 1000 items.
                }

            } catch (Exception ex) {
                logger.severe(" save exception " + ex.toString());
            }
        } catch (Exception ex) {
        }
        ;
    }


    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////
    public static String get_ticker_for_trade(String cur) throws SQLException, ClassNotFoundException {

        System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
        String sql = "BEGIN ? := LLC_TKS_MSC_UTILS.GET_TICKER_FOR_TRADE(?); END;";

        //String dbURL = "jdbc:oracle:thin:@RBR";
        String dbURL = property.getProperty("dbURL");
        //logger.severe(" get_rates_by_ticker ticker " + ticker);
        Class.forName("oracle.jdbc.OracleDriver");
        java.sql.Connection conn = null;

        String sErr_Msg;
        try (
                java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                CallableStatement statement = connection.prepareCall(sql);
        ) {
            statement.registerOutParameter(1, OracleTypes.VARCHAR);
            statement.setString(2, cur);
            // ...
            //logger.severe(" get_rates_by_ticker before execute ");
            statement.execute();
            return statement.getString(1);
            //logger.severe(" sErr_Msg " + sErr_Msg + " nums "+Arrays.toString(nums));
        } catch (Exception ex) {
            //logger.severe(" save_candles_search_params exception " + ex.toString());
            System.out.println("  get_ticker_for_trade exception " + ex.toString());

        }
        return "";
    }
    //////////////////////////////////////////////////////////////////////////////


    public static void get_candles(Context context, String sFigi, OffsetDateTime cycl_from, OffsetDateTime cycl_to, String sTicker, Logger logger, CandleInterval eCandleInterval) {
        logger.severe(" get_candles  sTicker " + sTicker + " cycl_from " + cycl_from + " cycl_to " + cycl_to + " Compare " + cycl_from.compareTo(cycl_to));
        if (cycl_from.compareTo(cycl_to) <= 0) {
            Long nId;
            List<Candle> lCandles;
            try {
                HistoricalCandles lHistoricalCandles;
                lHistoricalCandles = context.getMarketCandles(sFigi, cycl_from, cycl_to, eCandleInterval).get();
                //lHistoricalCandles = null;

                nId = save_candles_search_params(sFigi, sTicker, cycl_from, cycl_to, eCandleInterval, logger);

                lCandles = lHistoricalCandles.getCandles();
                save_candles(lCandles, sTicker, nId, logger);
            } catch (Exception ex) {
                logger.severe(" exception get_candles  " + ex.toString());
            }
        }
    }

    public static void db_log(String vProc, String vText, Logger logger) {
        try {
            System.setProperty("oracle.net.tns_admin", cTNS_ADMIN);
            String sql = "BEGIN TKS_LOG(?,?); END;";


            String dbURL = property.getProperty("dbURL");

            Class.forName("oracle.jdbc.OracleDriver");
            java.sql.Connection conn = null;

            String sErr_Msg;
            try (
                    java.sql.Connection connection = conn = DriverManager.getConnection(dbURL, cUSER, cPWD);
                    CallableStatement statement = connection.prepareCall(sql);
            ) {

                statement.setString(1, vProc);
                statement.setString(2, vText);
                statement.execute();


            } catch (Exception ex) {

                System.out.println("  db_log exception " + ex.toString());
                logger.severe(" exception db_log  " + ex.toString());
            }

    }catch(Exception ex)
        {logger.severe(" exception db_log  " + ex.toString());}
}
}
