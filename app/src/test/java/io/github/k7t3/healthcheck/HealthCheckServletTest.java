package io.github.k7t3.healthcheck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HealthCheckServletTest {

    // ============================================================
    // resolveDatabaseConfiguration
    // ============================================================

    @Test
    void readsDatabaseConfigurationFromServletContextInitParameters() {
        var servletContext = mock(ServletContext.class);
        when(servletContext.getInitParameter("db.url")).thenReturn("jdbc:postgresql://localhost:5432/appdb");
        when(servletContext.getInitParameter("db.user")).thenReturn("appuser");
        when(servletContext.getInitParameter("db.password")).thenReturn("secret");

        var servlet = new HealthCheckServlet();
        var dbConfig = servlet.resolveDatabaseConfiguration(servletContext);

        assertThat(dbConfig.url()).isEqualTo("jdbc:postgresql://localhost:5432/appdb");
        assertThat(dbConfig.user()).isEqualTo("appuser");
        assertThat(dbConfig.password()).isEqualTo("secret");
    }

    @Test
    void treatsBlankDatabaseUrlAsMissingConfiguration() {
        var servletContext = mock(ServletContext.class);
        when(servletContext.getInitParameter("db.url")).thenReturn("   ");

        var servlet = new HealthCheckServlet();
        var dbConfig = servlet.resolveDatabaseConfiguration(servletContext);

        assertThat(dbConfig.isConfigured()).isFalse();
    }

    @Test
    void trimsWhitespaceFromUserAndPassword() {
        var servletContext = mock(ServletContext.class);
        when(servletContext.getInitParameter("db.url")).thenReturn("jdbc:postgresql://localhost:5432/appdb");
        when(servletContext.getInitParameter("db.user")).thenReturn("  appuser  ");
        when(servletContext.getInitParameter("db.password")).thenReturn("  secret  ");

        var servlet = new HealthCheckServlet();
        var dbConfig = servlet.resolveDatabaseConfiguration(servletContext);

        assertThat(dbConfig.isConfigured()).isTrue();
        assertThat(dbConfig.user()).isEqualTo("appuser");
        assertThat(dbConfig.password()).isEqualTo("secret");
    }

    @Test
    void treatsBlankUserAndPasswordAsNull() {
        var servletContext = mock(ServletContext.class);
        when(servletContext.getInitParameter("db.url")).thenReturn("jdbc:postgresql://localhost:5432/appdb");
        when(servletContext.getInitParameter("db.user")).thenReturn("");
        when(servletContext.getInitParameter("db.password")).thenReturn("   ");

        var servlet = new HealthCheckServlet();
        var dbConfig = servlet.resolveDatabaseConfiguration(servletContext);

        assertThat(dbConfig.isConfigured()).isTrue();
        assertThat(dbConfig.user()).isNull();
        assertThat(dbConfig.password()).isNull();
    }

    // ============================================================
    // trimToNull
    // ============================================================

    @Test
    void trimToNullReturnsNullForNullInput() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.trimToNull(null)).isNull();
    }

    @Test
    void trimToNullReturnsNullForEmptyString() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.trimToNull("")).isNull();
    }

    @Test
    void trimToNullReturnsNullForWhitespaceOnly() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.trimToNull("   \t  ")).isNull();
    }

    @Test
    void trimToNullTrimsLeadingAndTrailingWhitespace() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.trimToNull("  hello  ")).isEqualTo("hello");
    }

    @Test
    void trimToNullReturnsUnchangedForNoWhitespace() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.trimToNull("hello")).isEqualTo("hello");
    }

    // ============================================================
    // escapeHtml
    // ============================================================

    @Test
    void escapeHtmlReturnsEmptyForNullInput() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml(null)).isEmpty();
    }

    @Test
    void escapeHtmlEscapesAmpersand() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml("a & b")).isEqualTo("a &amp; b");
    }

    @Test
    void escapeHtmlEscapesLessThan() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml("a < b")).isEqualTo("a &lt; b");
    }

    @Test
    void escapeHtmlEscapesGreaterThan() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml("a > b")).isEqualTo("a &gt; b");
    }

    @Test
    void escapeHtmlEscapesDoubleQuote() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml("a \"b\" c")).isEqualTo("a &quot;b&quot; c");
    }

    @Test
    void escapeHtmlEscapesMultipleSpecialCharacters() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml("<script>alert(\"&\")</script>"))
                .isEqualTo("&lt;script&gt;alert(&quot;&amp;&quot;)&lt;/script&gt;");
    }

    @Test
    void escapeHtmlReturnsUnchangedForPlainText() {
        var servlet = new HealthCheckServlet();

        assertThat(servlet.escapeHtml("Hello World 123")).isEqualTo("Hello World 123");
    }

    // ============================================================
    // doGet
    // ============================================================

    @Nested
    class DoGet {

        private HttpServletRequest defaultRequest() {
            var request = mock(HttpServletRequest.class);
            when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getScheme()).thenReturn("http");
            return request;
        }

        private String executeDoGet(ServletContext servletContext, HttpServletRequest request) throws Exception {
            var servlet = spy(HealthCheckServlet.class);
            doReturn(servletContext).when(servlet).getServletContext();

            var response = mock(HttpServletResponse.class);
            var stringWriter = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

            servlet.doGet(request, response);
            return stringWriter.toString();
        }

        @Test
        void setsHtmlContentType() throws Exception {
            var servlet = spy(HealthCheckServlet.class);
            var servletContext = mock(ServletContext.class);
            doReturn(servletContext).when(servlet).getServletContext();
            when(servletContext.getServerInfo()).thenReturn("Test Server");

            var response = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            var request = defaultRequest();

            servlet.doGet(request, response);

            verify(response).setContentType("text/html; charset=UTF-8");
        }

        @Test
        void outputsServerInfo() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Apache Tomcat/9.0");
            when(servletContext.getMajorVersion()).thenReturn(4);
            when(servletContext.getMinorVersion()).thenReturn(0);

            var request = defaultRequest();
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            when(request.getScheme()).thenReturn("https");

            var content = executeDoGet(servletContext, request);

            assertThat(content).contains("Apache Tomcat/9.0");
            assertThat(content).contains("4.0");
            assertThat(content).contains(System.getProperty("java.version"));
        }

        @Test
        void showsNotConfiguredWhenDbIsNotConfigured() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();

            var content = executeDoGet(servletContext, request);

            assertThat(content).contains("Database is not configured");
        }

        @Test
        void showsForwardedHeadersWhenPresent() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();
            when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
            when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
            when(request.getHeader("X-Forwarded-Host")).thenReturn("example.com");

            var content = executeDoGet(servletContext, request);

            assertThat(content).contains("X-Forwarded-For: 10.0.0.1");
            assertThat(content).contains("X-Forwarded-Proto: https");
            assertThat(content).contains("X-Forwarded-Host: example.com");
        }

        @Test
        void hidesForwardedHeadersWhenAbsent() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();

            var content = executeDoGet(servletContext, request);

            assertThat(content).doesNotContain("X-Forwarded-For");
            assertThat(content).doesNotContain("X-Forwarded-Proto");
            assertThat(content).doesNotContain("X-Forwarded-Host");
        }

        @Test
        void rendersAllRequestHeadersTable() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();
            var headerNames = Collections.enumeration(List.of("Host", "Accept"));
            when(request.getHeaderNames()).thenReturn(headerNames);
            when(request.getHeader("Host")).thenReturn("localhost");
            when(request.getHeader("Accept")).thenReturn("text/html");

            var content = executeDoGet(servletContext, request);

            assertThat(content).contains("<td>Host</td><td>localhost</td>");
            assertThat(content).contains("<td>Accept</td><td>text/html</td>");
        }

        @Test
        void escapesHtmlInHeaderValues() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();
            var headerNames = Collections.enumeration(List.of("X-Malicious"));
            when(request.getHeaderNames()).thenReturn(headerNames);
            when(request.getHeader("X-Malicious")).thenReturn("<script>alert('xss')</script>");

            var content = executeDoGet(servletContext, request);

            assertThat(content).contains("&lt;script&gt;");
            assertThat(content).doesNotContain("<script>alert");
        }

        @Test
        void outputsRemoteAddressAndScheme() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();
            when(request.getRemoteAddr()).thenReturn("192.168.0.1");
            when(request.getScheme()).thenReturn("https");

            var content = executeDoGet(servletContext, request);

            assertThat(content).contains("Remote Address: 192.168.0.1");
            assertThat(content).contains("Scheme: https");
        }

        @Test
        @DisplayName("Should not render table section when database is not configured")
        void doesNotRenderTableSectionWhenDatabaseNotConfigured() throws Exception {
            var servletContext = mock(ServletContext.class);
            when(servletContext.getServerInfo()).thenReturn("Test");

            var request = defaultRequest();

            var content = executeDoGet(servletContext, request);

            assertThat(content).doesNotContain("Database Tables");
        }
    }

    // ============================================================
    // fetchTableMetadata
    // ============================================================

    @Nested
    @DisplayName("fetchTableMetadata")
    class FetchTableMetadata {

        @Test
        @DisplayName("Should return empty list when no tables exist")
        void shouldReturnEmptyListWhenNoTablesExist() throws SQLException {
            var conn = mock(Connection.class);
            var tablesPstmt = mock(PreparedStatement.class);
            var tablesRs = mock(ResultSet.class);

            when(conn.prepareStatement(startsWith("SELECT table_name"))).thenReturn(tablesPstmt);
            when(tablesPstmt.executeQuery()).thenReturn(tablesRs);
            when(tablesRs.next()).thenReturn(false);

            var servlet = new HealthCheckServlet();
            var result = servlet.fetchTableMetadata(conn);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return tables with their columns")
        void shouldReturnTablesWithColumns() throws SQLException {
            var conn = mock(Connection.class);
            var tablesPstmt = mock(PreparedStatement.class);
            var tablesRs = mock(ResultSet.class);
            var colsPstmt = mock(PreparedStatement.class);
            var usersColsRs = mock(ResultSet.class);
            var ordersColsRs = mock(ResultSet.class);

            when(conn.prepareStatement(startsWith("SELECT table_name"))).thenReturn(tablesPstmt);
            when(conn.prepareStatement(startsWith("SELECT column_name"))).thenReturn(colsPstmt);

            when(tablesPstmt.executeQuery()).thenReturn(tablesRs);
            when(tablesRs.next()).thenReturn(true, true, false);
            when(tablesRs.getString("table_name")).thenReturn("users", "orders");

            when(colsPstmt.executeQuery()).thenReturn(usersColsRs, ordersColsRs);

            // users columns: id (integer, NO), name (varchar, YES)
            when(usersColsRs.next()).thenReturn(true, true, false);
            when(usersColsRs.getString("column_name")).thenReturn("id", "name");
            when(usersColsRs.getString("data_type")).thenReturn("integer", "character varying");
            when(usersColsRs.getString("is_nullable")).thenReturn("NO", "YES");

            // orders columns: order_id (bigint, NO), total (numeric, YES)
            when(ordersColsRs.next()).thenReturn(true, true, false);
            when(ordersColsRs.getString("column_name")).thenReturn("order_id", "total");
            when(ordersColsRs.getString("data_type")).thenReturn("bigint", "numeric");
            when(ordersColsRs.getString("is_nullable")).thenReturn("NO", "YES");

            var servlet = new HealthCheckServlet();
            var result = servlet.fetchTableMetadata(conn);

            assertThat(result).hasSize(2);

            assertThat(result.get(0).tableName()).isEqualTo("users");
            assertThat(result.get(0).columns()).hasSize(2);
            assertThat(result.get(0).columns().get(0).columnName()).isEqualTo("id");
            assertThat(result.get(0).columns().get(0).dataType()).isEqualTo("integer");
            assertThat(result.get(0).columns().get(0).nullable()).isFalse();
            assertThat(result.get(0).columns().get(1).columnName()).isEqualTo("name");
            assertThat(result.get(0).columns().get(1).dataType()).isEqualTo("character varying");
            assertThat(result.get(0).columns().get(1).nullable()).isTrue();

            assertThat(result.get(1).tableName()).isEqualTo("orders");
            assertThat(result.get(1).columns()).hasSize(2);

            verify(colsPstmt).setString(1, "users");
            verify(colsPstmt).setString(1, "orders");
        }

        @Test
        @DisplayName("Should propagate SQLException from tables query")
        void shouldPropagateSQLExceptionFromTablesQuery() throws SQLException {
            var conn = mock(Connection.class);
            var tablesPstmt = mock(PreparedStatement.class);

            when(conn.prepareStatement(startsWith("SELECT table_name"))).thenReturn(tablesPstmt);
            when(tablesPstmt.executeQuery()).thenThrow(new SQLException("Connection lost"));

            var servlet = new HealthCheckServlet();

            assertThatThrownBy(() -> servlet.fetchTableMetadata(conn))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Connection lost");
        }

        @Test
        @DisplayName("Should propagate SQLException from columns query")
        void shouldPropagateSQLExceptionFromColumnsQuery() throws SQLException {
            var conn = mock(Connection.class);
            var tablesPstmt = mock(PreparedStatement.class);
            var tablesRs = mock(ResultSet.class);
            var colsPstmt = mock(PreparedStatement.class);

            when(conn.prepareStatement(startsWith("SELECT table_name"))).thenReturn(tablesPstmt);
            when(conn.prepareStatement(startsWith("SELECT column_name"))).thenReturn(colsPstmt);

            when(tablesPstmt.executeQuery()).thenReturn(tablesRs);
            when(tablesRs.next()).thenReturn(true, false);
            when(tablesRs.getString("table_name")).thenReturn("users");

            when(colsPstmt.executeQuery()).thenThrow(new SQLException("Column query failed"));

            var servlet = new HealthCheckServlet();

            assertThatThrownBy(() -> servlet.fetchTableMetadata(conn))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Column query failed");
        }
    }

    // ============================================================
    // renderTableMetadata
    // ============================================================

    @Nested
    @DisplayName("renderTableMetadata")
    class RenderTableMetadata {

        @Test
        @DisplayName("Should render no-tables message when table list is empty")
        void shouldRenderNoTablesMessageWhenEmpty() {
            var stringWriter = new StringWriter();
            var out = new PrintWriter(stringWriter);

            new HealthCheckServlet().renderTableMetadata(out, List.of());

            var content = stringWriter.toString();
            assertThat(content).contains("No tables found");
        }

        @Test
        @DisplayName("Should render all tables with column details")
        void shouldRenderAllTablesWithColumnDetails() {
            var stringWriter = new StringWriter();
            var out = new PrintWriter(stringWriter);

            var columns = List.of(
                    new HealthCheckServlet.ColumnInfo("id", "integer", false),
                    new HealthCheckServlet.ColumnInfo("name", "character varying", true)
            );
            var tables = List.of(
                    new HealthCheckServlet.TableInfo("users", columns)
            );

            new HealthCheckServlet().renderTableMetadata(out, tables);

            var content = stringWriter.toString();
            assertThat(content).contains("Database Tables");
            assertThat(content).contains("users");
            assertThat(content).contains("<td>id</td>");
            assertThat(content).contains("<td>integer</td>");
            assertThat(content).contains("<td>NO</td>");
            assertThat(content).contains("<td>name</td>");
            assertThat(content).contains("<td>character varying</td>");
            assertThat(content).contains("<td>YES</td>");
        }

        @Test
        @DisplayName("Should escape HTML in table and column names")
        void shouldEscapeHtmlInNames() {
            var stringWriter = new StringWriter();
            var out = new PrintWriter(stringWriter);

            var columns = List.of(
                    new HealthCheckServlet.ColumnInfo("<script>", "int", false)
            );
            var tables = List.of(
                    new HealthCheckServlet.TableInfo("<evil>", columns)
            );

            new HealthCheckServlet().renderTableMetadata(out, tables);

            var content = stringWriter.toString();
            assertThat(content).contains("&lt;script&gt;");
            assertThat(content).contains("&lt;evil&gt;");
            assertThat(content).doesNotContain("<script>");
            assertThat(content).doesNotContain("<evil>");
        }
    }
}
