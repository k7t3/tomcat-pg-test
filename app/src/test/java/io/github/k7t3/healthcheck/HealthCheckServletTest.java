package io.github.k7t3.healthcheck;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    }
}
