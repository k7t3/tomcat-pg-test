package io.github.k7t3.healthcheck;

import org.junit.jupiter.api.Test;

import javax.servlet.ServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HealthCheckServlet} のユニットテスト。
 *
 * <p>サーブレットコンテキストの初期パラメータからのデータベース設定解決を検証します。</p>
 */
class HealthCheckServletTest {

    /**
     * 3つのデータベース接続パラメータがサーブレットコンテキストの
     * 初期パラメータから正しく読み取られることを検証します。
     */
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

    /**
     * 空白または空白のみのデータベースURLが未設定として扱われ、
     * {@code isConfigured()} が {@code false} を返すことを検証します。
     */
    @Test
    void treatsBlankDatabaseUrlAsMissingConfiguration() {
        var servletContext = mock(ServletContext.class);
        when(servletContext.getInitParameter("db.url")).thenReturn("   ");

        var servlet = new HealthCheckServlet();

        var dbConfig = servlet.resolveDatabaseConfiguration(servletContext);

        assertThat(dbConfig.isConfigured()).isFalse();
    }
}
