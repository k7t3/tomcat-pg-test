package io.github.k7t3.healthcheck;

import org.junit.jupiter.api.Test;

import javax.servlet.ServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthCheckServletTest {

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
}
