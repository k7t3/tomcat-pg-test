#!/bin/sh
set -eu

mkdir -p /usr/local/tomcat/conf/Catalina/localhost

cat > /usr/local/tomcat/conf/Catalina/localhost/ROOT.xml <<EOF
<Context>
    <Parameter name="db.url" value="${DB_URL:-}" override="false"/>
    <Parameter name="db.user" value="${DB_USER:-}" override="false"/>
    <Parameter name="db.password" value="${DB_PASSWORD:-}" override="false"/>
</Context>
EOF

exec catalina.sh run
