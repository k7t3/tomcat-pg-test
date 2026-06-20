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
import java.util.Enumeration;

@WebServlet("/health")
public class HealthCheckServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();

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
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUrl == null || dbUrl.isEmpty()) {
            out.println("<p style='color:orange'>DB_URL environment variable is not set. Skipping database check.</p>");
        } else {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                    boolean valid = conn.isValid(5);
                    out.println("<p style='color:green'>Database connection: " + (valid ? "OK" : "FAILED") + "</p>");
                    out.println("<p>Connected to: " + dbUrl + "</p>");
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

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
