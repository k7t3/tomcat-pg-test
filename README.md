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

## GitHub Release

GitHub Release を `published` にすると、GitHub Actions の `Release WAR` ワークフローが `:app:war` を実行し、生成された WAR を Release asset として添付します。

## デプロイ

生成された WAR を Tomcat 9 の `webapps/` に配置してください。

## Docker での動作確認

Tomcat 9 + Java 21 + PostgreSQL 18 を `docker compose` で起動できます。

```bash
docker compose up --build
```

起動後の確認先:

- アプリ: `http://localhost:8080/`
- ヘルスチェック: `http://localhost:8080/health`

`app` コンテナは起動時に `ROOT.xml` を生成し、以下の環境変数を Tomcat の `context-param` として渡します。

PostgreSQL 18 の公式イメージ仕様に合わせて、永続ボリュームは `/var/lib/postgresql` にマウントしています。

| 環境変数 | 説明 |
|----------|------|
| `DB_URL` | JDBC接続URL |
| `DB_USER` | データベースユーザー名 |
| `DB_PASSWORD` | データベースパスワード |

サンプルデータとして PostgreSQL に `customers` テーブルを自動作成するため、`/health` で DB 接続だけでなくテーブル一覧も確認できます。

### スモークテスト

以下のスクリプトで、Docker 起動から `/health` の確認までまとめて実行できます。

```bash
sh scripts/docker-smoke-test.sh
```

## アプリケーション単位の設定

データベース接続テストは、Tomcat 起動プロセスの環境変数ではなく、WAR ごとの `ServletContext` 初期化パラメータを使います。

設定キー:

| キー | 説明 |
|------|------|
| `db.url` | JDBC接続URL（例: `jdbc:postgresql://localhost:5432/mydb`） |
| `db.user` | データベースユーザー名 |
| `db.password` | データベースパスワード |

未設定の場合、DB 接続テストはスキップされます。

### 設定方法 1: WAR 内の `WEB-INF/web.xml`

`app/src/main/webapp/WEB-INF/web.xml` に空の `context-param` を定義しています。必要に応じて値を設定してください。

### 設定方法 2: Tomcat 側でアプリごとに上書き

Tomcat では `conf/Catalina/localhost/app.xml` のようなコンテキスト定義で、WAR ごとに設定できます。

```xml
<Context>
    <Parameter name="db.url" value="jdbc:postgresql://localhost:5432/mydb" override="false"/>
    <Parameter name="db.user" value="myuser" override="false"/>
    <Parameter name="db.password" value="mypassword" override="false"/>
</Context>
```

この方法なら同じ Tomcat 上の別 WAR と独立して設定できます。

## エンドポイント

### `/health`

サーバーのヘルスチェック情報をHTMLで表示します。

- **Application Server** — サーブレットコンテナ情報、Servlet APIバージョン、Javaバージョン
- **Database Connectivity** — PostgreSQL への接続テスト（`db.url` / `db.user` / `db.password` 要）
- **Reverse Proxy / Request Headers** — `X-Forwarded-For`、`X-Forwarded-Proto`、`X-Forwarded-Host` などのプロキシヘッダー、全リクエストヘッダー一覧

### `/`

トップページ。`/health` へのリンクを表示します。
