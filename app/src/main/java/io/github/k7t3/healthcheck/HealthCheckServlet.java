package io.github.k7t3.healthcheck;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * サーバーおよびデータベース接続の状態を提供するヘルスチェックサーブレット。
 *
 * <p>{@code /health} エンドポイントへのGETリクエストに対して、
 * アプリケーションサーバー情報、PostgreSQLデータベース接続状態、
 * リバースプロキシのリクエストヘッダーを含むHTMLページを返します。</p>
 *
 * <p>データベース接続は {@code web.xml} またはTomcatコンテキストファイルで設定された
 * {@code db.url}, {@code db.user}, {@code db.password} のコンテキストパラメータを使用してテストされます。</p>
 *
 * @author k7t3
 * @since 0.1.0
 */
@WebServlet("/health")
public class HealthCheckServlet extends HttpServlet {

    /** データベースJDBC URLのコンテキストパラメータ名。 */
    static final String DB_URL_PARAM = "db.url";

    /** データベースユーザーのコンテキストパラメータ名。 */
    static final String DB_USER_PARAM = "db.user";

    /** データベースパスワードのコンテキストパラメータ名。 */
    static final String DB_PASSWORD_PARAM = "db.password";

    /**
     * HTTP GETリクエストを処理し、HTMLのヘルスチェックレポートを生成します。
     *
     * <p>レポートにはサーブレットコンテナ情報、データベース接続テスト結果、
     * リバースプロキシのリクエストヘッダーが含まれます。</p>
     *
     * @param req HTTPリクエスト
     * @param resp HTTPレスポンス
     * @throws ServletException サーブレットエラーが発生した場合
     * @throws IOException レスポンス書き込み時にI/Oエラーが発生した場合
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=UTF-8");
        var out = resp.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Health Check</title></head><body>");
        out.println("<h1>Server Health Check</h1>");

        // Application Server info
        out.println("<h2>Application Server</h2>");
        out.println("<ul>");
        out.println("<li>Servlet Container: " + getServletContext().getServerInfo() + "</li>");
        out.println("<li>Servlet API Version: " + getServletContext().getMajorVersion() + "." + getServletContext().getMinorVersion() + "</li>");
        out.println("<li>Java Version: " + System.getProperty("java.version") + "</li>");
        out.println("</ul>");

        // Database connectivity
        out.println("<h2>Database Connectivity</h2>");
        var dbConfig = resolveDatabaseConfiguration(getServletContext());

        if (!dbConfig.isConfigured()) {
            out.println("<p style='color:orange'>Database is not configured. Set application context parameters db.url, db.user, and db.password.</p>");
        } else {
            try {
                Class.forName("org.postgresql.Driver");
                try (var conn = DriverManager.getConnection(dbConfig.url(), dbConfig.user(), dbConfig.password())) {
                    var valid = conn.isValid(5);
                    out.println("<p style='color:green'>Database connection: " + (valid ? "OK" : "FAILED") + "</p>");
                    out.println("<p>Connected to: " + dbConfig.url() + "</p>");

                    if (valid) {
                        try {
                            var tables = fetchTableMetadata(conn);
                            renderTableMetadata(out, tables);
                        } catch (SQLException e) {
                            out.println("<p style='color:red'>Failed to fetch table metadata: " + escapeHtml(e.getMessage()) + "</p>");
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                out.println("<p style='color:red'>PostgreSQL JDBC Driver not found: " + e.getMessage() + "</p>");
            } catch (SQLException e) {
                out.println("<p style='color:red'>Database connection failed: " + e.getMessage() + "</p>");
            }
        }

        // Reverse Proxy (IIS) headers
        out.println("<h2>Reverse Proxy / Request Headers</h2>");
        out.println("<ul>");
        out.println("<li>Remote Address: " + req.getRemoteAddr() + "</li>");
        out.println("<li>Scheme: " + req.getScheme() + "</li>");

        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            out.println("<li>X-Forwarded-For: " + forwardedFor + "</li>");
        }
        String forwardedProto = req.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            out.println("<li>X-Forwarded-Proto: " + forwardedProto + "</li>");
        }
        String forwardedHost = req.getHeader("X-Forwarded-Host");
        if (forwardedHost != null) {
            out.println("<li>X-Forwarded-Host: " + forwardedHost + "</li>");
        }

        out.println("</ul>");

        // All headers for debugging
        out.println("<h3>All Request Headers</h3>");
        out.println("<table border='1'><tr><th>Header</th><th>Value</th></tr>");
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            out.println("<tr><td>" + escapeHtml(name) + "</td><td>" + escapeHtml(req.getHeader(name)) + "</td></tr>");
        }
        out.println("</table>");

        out.println("</body></html>");
    }

    /**
     * サーブレットコンテキストからデータベース接続パラメータを解決します。
     *
     * <p>空文字または空白のみのパラメータ値は未設定として扱います。</p>
     *
     * @param servletContext 初期パラメータを含むサーブレットコンテキスト
     * @return コンテキストパラメータから生成された {@link DatabaseConfiguration}
     */
    DatabaseConfiguration resolveDatabaseConfiguration(javax.servlet.ServletContext servletContext) {
        return new DatabaseConfiguration(
                trimToNull(servletContext.getInitParameter(DB_URL_PARAM)),
                trimToNull(servletContext.getInitParameter(DB_USER_PARAM)),
                trimToNull(servletContext.getInitParameter(DB_PASSWORD_PARAM))
        );
    }

    /**
     * 値が {@code null}、空文字、または空白のみの場合に {@code null} を返します。
     *
     * @param value トリムする入力文字列
     * @return トリムされた文字列。空白の場合は {@code null}
     */
    String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * XSSを防ぐためにHTML特殊文字をエスケープします。
     *
     * <p>以下の文字が置換されます:
     * {@code &}, {@code <}, {@code >}, {@code "}</p>
     *
     * @param input 生の入力文字列（{@code null} 可）
     * @return HTMLエスケープされた文字列。入力が {@code null} の場合は空文字列
     */
    String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * データベース接続からテーブルメタデータを取得します。
     *
     * <p>{@code information_schema.tables} からpublicスキーマのベーステーブル一覧を取得し、
     * 各テーブルについて {@code information_schema.columns} からカラム情報を取得します。</p>
     *
     * @param conn データベース接続
     * @return テーブル情報のリスト（テーブルが存在しない場合は空リスト）
     * @throws SQLException データベースクエリが失敗した場合
     */
    List<TableInfo> fetchTableMetadata(Connection conn) throws SQLException {
        var tablesSql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;

        var columnsSql = """
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """;

        var tables = new ArrayList<TableInfo>();

        try (var stmt = conn.prepareStatement(tablesSql);
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var tableName = rs.getString("table_name");
                var columns = new ArrayList<ColumnInfo>();

                try (var colStmt = conn.prepareStatement(columnsSql)) {
                    colStmt.setString(1, tableName);
                    try (var colRs = colStmt.executeQuery()) {
                        while (colRs.next()) {
                            columns.add(new ColumnInfo(
                                    colRs.getString("column_name"),
                                    colRs.getString("data_type"),
                                    "YES".equals(colRs.getString("is_nullable"))
                            ));
                        }
                    }
                }
                tables.add(new TableInfo(tableName, columns));
            }
        }
        return tables;
    }

    /**
     * テーブルメタデータをHTMLとしてレンダリングします。
     *
     * <p>テーブル一覧と各テーブルのカラム情報（名前、型、NULL許可）を表示します。
     * テーブルが存在しない場合はその旨を表示します。</p>
     *
     * @param out    HTML出力先
     * @param tables 表示するテーブル情報のリスト
     */
    void renderTableMetadata(PrintWriter out, List<TableInfo> tables) {
        out.println("<h2>Database Tables</h2>");
        if (tables.isEmpty()) {
            out.println("<p>No tables found in public schema.</p>");
            return;
        }
        for (var table : tables) {
            out.println("<h3>" + escapeHtml(table.tableName()) + "</h3>");
            out.println("<table border='1'><tr><th>Column</th><th>Type</th><th>Nullable</th></tr>");
            for (var col : table.columns()) {
                out.println("<tr><td>" + escapeHtml(col.columnName()) + "</td>"
                        + "<td>" + escapeHtml(col.dataType()) + "</td>"
                        + "<td>" + (col.nullable() ? "YES" : "NO") + "</td></tr>");
            }
            out.println("</table>");
        }
    }

    /**
     * テーブルの1カラムに関するメタデータ。
     *
     * @param columnName カラム名
     * @param dataType   データ型
     * @param nullable   NULL許容
     */
    record ColumnInfo(String columnName, String dataType, boolean nullable) {}

    /**
     * 1テーブルに関するメタデータ。
     *
     * @param tableName テーブル名
     * @param columns   カラム情報のリスト
     */
    record TableInfo(String tableName, List<ColumnInfo> columns) {}

    /**
     * サーブレットコンテキストの初期パラメータから抽出されたデータベース接続パラメータを保持するレコード。
     *
     * @param url JDBC URL（未設定の場合は {@code null}）
     * @param user データベースユーザー名
     * @param password データベースパスワード
     */
    record DatabaseConfiguration(String url, String user, String password) {
        /**
         * URLパラメータをチェックしてデータベースが設定済みかどうかを返します。
         *
         * @return URLがnullでない場合は {@code true}
         */
        boolean isConfigured() {
            return url != null;
        }
    }
}
