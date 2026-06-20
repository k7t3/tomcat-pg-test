# tomcat-pg-test

Tomcat 9 + PostgreSQL の動作確認用サーブレット Web アプリケーション。

## 技術スタック

| 項目 | バージョン |
|------|-----------|
| Java | 21 |
| Gradle | 9.5.1 |
| Servlet API | 4.0.1 (javax) |
| PostgreSQL JDBC | 42.7.11 |

## プロジェクト構成

```
tomcat-pg-test/
├── .java-version
├── build.gradle              # ルートプロジェクト共通設定
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle          # WAR プラグイン + 依存関係
    └── src/
        ├── main/
        │   ├── java/io/github/k7t3/healthcheck/
        │   │   └── HealthCheckServlet.java
        │   ├── resources/
        │   └── webapp/
        │       ├── index.html
        │       └── WEB-INF/
        │           └── web.xml
        └── test/
```

## ビルド

```bash
./gradlew build
```

WAR ファイルは `app/build/libs/app-0.1.0-SNAPSHOT.war` に生成されます。

## デプロイ

生成された WAR を Tomcat 9 の `webapps/` に配置してください。

## 環境変数

データベース接続テストには以下の環境変数を設定します（Tomcat 起動時に指定）。

| 変数 | 説明 |
|------|------|
| `DB_URL` | JDBC接続URL（例: `jdbc:postgresql://localhost:5432/mydb`） |
| `DB_USER` | データベースユーザー名 |
| `DB_PASSWORD` | データベースパスワード |

環境変数が未設定の場合、DB接続テストはスキップされ、他の項目のみ表示されます。

## エンドポイント

### `/health`

サーバーのヘルスチェック情報をHTMLで表示します。

- **Application Server** — サーブレットコンテナ情報、Servlet APIバージョン、Javaバージョン
- **Database Connectivity** — PostgreSQLへの接続テスト（環境変数 `DB_URL` / `DB_USER` / `DB_PASSWORD` 要）
- **Reverse Proxy / Request Headers** — `X-Forwarded-For`、`X-Forwarded-Proto`、`X-Forwarded-Host` などのプロキシヘッダー、全リクエストヘッダー一覧

### `/`

トップページ。`/health` へのリンクを表示します。
