package com.kern.infrastructure.apps.nginx

object NginxConfigTemplates {

    val definitions = listOf(
        "listenPort" to NginxFieldDef("Порт", "80", "Сетевой порт HTTP", "Сервер"),
        "serverName" to NginxFieldDef("Имя сервера", "_", "Директива server_name", "Сервер"),
        "rootPath" to NginxFieldDef("Корневая директория", "/var/www/html", "Путь к файлам сайта", "Сервер"),
        "indexFiles" to NginxFieldDef("Index-файлы", "index.html index.htm", "Директива index", "Сервер"),
        "clientMaxBodySize" to NginxFieldDef("Макс. размер тела запроса", "1m", "client_max_body_size", "Дополнительно"),
    )

    val defaultValues: Map<String, String> =
        definitions.associate { (key, def) -> key to def.defaultValue }

    fun siteConfig(values: Map<String, String>): String {
        val listen = values["listenPort"] ?: "80"
        val serverName = values["serverName"] ?: "_"
        val root = values["rootPath"] ?: "/var/www/html"
        val index = values["indexFiles"] ?: "index.html"
        val bodySize = values["clientMaxBodySize"] ?: "1m"

        return """
# Managed by Kern
server {
    listen $listen default_server;
    listen [::]:$listen default_server;

    server_name $serverName;
    root $root;
    index $index;

    client_max_body_size $bodySize;

    location / {
        try_files ${'$'}uri ${'$'}uri/ =404;
    }
}
""".trimIndent()
    }
}

data class NginxFieldDef(
    val label: String,
    val defaultValue: String,
    val description: String,
    val section: String,
)
