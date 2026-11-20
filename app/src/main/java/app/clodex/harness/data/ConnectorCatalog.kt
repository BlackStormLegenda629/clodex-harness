package app.clodex.harness.data

/** Rich, offline metadata for every connector in the catalog. The DB stores only the connection status. */
data class ConnCatalogEntry(
    val id: String,
    val name: String,
    val developer: String,
    val description: String,
    val longDescription: String,
    val color: String,
    val category: String,
    val badge: String? = null,
    val tools: List<String> = emptyList(),
    val site: String = "",
)

object ConnectorCatalog {
    val categories = listOf("Все", "Популярные", "Продуктивность", "Разработка", "Коммуникации", "Дизайн", "Данные", "Платежи")

    val all: List<ConnCatalogEntry> = listOf(
        ConnCatalogEntry(
            "gm", "Gmail", "Google", "Чтение и поиск по почте",
            "Читайте письма, ищите по почте и просматривайте метки и черновики. Clodex не отправляет и не изменяет письма через этот коннектор.",
            "#D93025", "Коммуникации", "Популярный", listOf("Поиск писем", "Черновики", "Метки", "Вложения"), "mail.google.com",
        ),
        ConnCatalogEntry(
            "gd", "Google Drive", "Google", "Поиск, чтение и загрузка файлов",
            "Находите документы по названию и содержимому, читайте таблицы и презентации, загружайте новые файлы в выбранные папки.",
            "#1FA463", "Данные", "Популярный", listOf("Поиск файлов", "Чтение", "Загрузка", "Папки"), "drive.google.com",
        ),
        ConnCatalogEntry(
            "gc", "Google Calendar", "Google", "Расписание, события и встречи",
            "Смотрите ближайшие события, создавайте встречи, ищите свободные слоты и переносите созвоны одной командой.",
            "#1A73E8", "Продуктивность", null, listOf("События", "Создание встреч", "Свободные слоты"), "calendar.google.com",
        ),
        ConnCatalogEntry(
            "gdocs", "Google Docs", "Google", "Создание и правка документов",
            "Создавайте документы, дописывайте разделы и предлагайте правки с сохранением форматирования и комментариев.",
            "#4285F4", "Продуктивность", "Новинка", listOf("Чтение", "Создание", "Комментарии"), "docs.google.com",
        ),
        ConnCatalogEntry(
            "gs", "Google Sheets", "Google", "Чтение и обновление таблиц",
            "Читайте диапазоны, добавляйте строки, пересчитывайте формулы и стройте сводки по вашим таблицам.",
            "#188038", "Данные", null, listOf("Диапазоны", "Формулы", "Сводки"), "sheets.google.com",
        ),
        ConnCatalogEntry(
            "gsl", "Google Slides", "Google", "Презентации и слайды",
            "Собирайте колоду из текста и заметок, правьте слайды и экспортируйте готовую презентацию.",
            "#F4B400", "Дизайн", "Новинка", listOf("Слайды", "Заметки", "Экспорт"), "slides.google.com",
        ),
        ConnCatalogEntry(
            "cv", "Canva", "Canva", "Поиск, создание и экспорт дизайнов",
            "Находите шаблоны, создавайте постеры и сторис, меняйте тексты и цвета и забирайте готовый экспорт.",
            "#7D2AE8", "Дизайн", "Популярный", listOf("Шаблоны", "Экспорт", "Брендкит"), "canva.com",
        ),
        ConnCatalogEntry(
            "fg", "Figma", "Figma", "Чтение макетов и компонентов",
            "Открывайте файлы и фреймы, читайте токены и компоненты, комментируйте макеты и сверяйте реализацию с дизайном.",
            "#A259FF", "Дизайн", "Популярный", listOf("Файлы", "Компоненты", "Комментарии", "Токены"), "figma.com",
        ),
        ConnCatalogEntry(
            "gh", "GitHub", "GitHub", "Репозитории, issues и pull request",
            "Смотрите код и историю, создавайте ветки, открывайте и ревьюйте pull request, работайте с issues и релизами.",
            "ink", "Разработка", "Популярный", listOf("Код", "Pull request", "Issues", "Actions"), "github.com",
        ),
        ConnCatalogEntry(
            "nt", "Notion", "Notion", "Поиск, обновление страниц и workflow",
            "Ищите страницы и базы, дописывайте заметки, обновляйте свойства и превращайте обсуждения в задачи.",
            "ink", "Продуктивность", "Популярный", listOf("Поиск", "Страницы", "Базы", "Задачи"), "notion.so",
        ),
        ConnCatalogEntry(
            "sl", "Slack", "Slack", "Сообщения, каналы и поиск",
            "Читайте каналы и треды, ищите решения, отправляйте сообщения и собирайте дайджесты обсуждений.",
            "#611F69", "Коммуникации", "Популярный", listOf("Каналы", "Треды", "Поиск", "Отправка"), "slack.com",
        ),
        ConnCatalogEntry(
            "ms", "Microsoft 365", "Microsoft", "Почта Outlook, календарь и файлы",
            "Работайте с Outlook, OneDrive и календарём: письма, встречи и документы в одном коннекторе.",
            "#D83B01", "Продуктивность", null, listOf("Outlook", "OneDrive", "Календарь"), "microsoft.com",
        ),
        ConnCatalogEntry(
            "sp", "Spotify", "Spotify", "Управление воспроизведением",
            "Ставьте музыку на паузу, переключайте треки, ищите плейлисты и собирайте подборки под задачу.",
            "#1DB954", "Коммуникации", null, listOf("Плейлисты", "Поиск", "Воспроизведение"), "spotify.com",
        ),
        ConnCatalogEntry(
            "ln", "Linear", "Linear", "Задачи, проекты и циклы",
            "Создавайте и обновляйте задачи, двигайте их по статусам, смотрите циклы и назначайте исполнителей.",
            "#5E6AD2", "Разработка", null, listOf("Задачи", "Циклы", "Проекты", "Статусы"), "linear.app",
        ),
        ConnCatalogEntry(
            "jr", "Jira", "Atlassian", "Эпики, задачи и спринты",
            "Ищите тикеты по JQL, создавайте задачи, меняйте статусы и подтягивайте детали спринта.",
            "#0052CC", "Разработка", null, listOf("JQL", "Задачи", "Спринты"), "atlassian.com/jira",
        ),
        ConnCatalogEntry(
            "cf", "Confluence", "Atlassian", "База знаний и документация",
            "Ищите страницы, читайте спецификации и обновляйте документацию команды без переключения вкладок.",
            "#2684FF", "Продуктивность", null, listOf("Поиск", "Страницы", "Обновление"), "atlassian.com/confluence",
        ),
        ConnCatalogEntry(
            "db", "Dropbox", "Dropbox", "Файлы, папки и общий доступ",
            "Ищите файлы, читайте документы, загружайте новые версии и делитесь ссылками с командой.",
            "#0061FF", "Данные", null, listOf("Поиск", "Загрузка", "Ссылки"), "dropbox.com",
        ),
        ConnCatalogEntry(
            "at", "Airtable", "Airtable", "Базы, записи и представления",
            "Читайте и создавайте записи, фильтруйте представления и собирайте данные из нескольких таблиц.",
            "#18BFFF", "Данные", null, listOf("Записи", "Представления", "Фильтры"), "airtable.com",
        ),
        ConnCatalogEntry(
            "as", "Asana", "Asana", "Задачи и проектные планы",
            "Создавайте задачи, назначайте ответственных, ставьте сроки и отслеживайте прогресс проекта.",
            "#F06A6A", "Продуктивность", null, listOf("Задачи", "Сроки", "Проекты"), "asana.com",
        ),
        ConnCatalogEntry(
            "tr", "Trello", "Atlassian", "Доски, списки и карточки",
            "Смотрите доски, добавляйте карточки, переносите их между списками и оставляйте комментарии.",
            "#0052CC", "Продуктивность", null, listOf("Доски", "Карточки", "Комментарии"), "trello.com",
        ),
        ConnCatalogEntry(
            "dc", "Discord", "Discord", "Серверы, каналы и сообщения",
            "Читайте каналы, ищите обсуждения, отправляйте сообщения и собирайте контекст из истории сервера.",
            "#5865F2", "Коммуникации", null, listOf("Каналы", "Сообщения", "Поиск"), "discord.com",
        ),
        ConnCatalogEntry(
            "tg", "Telegram", "Telegram", "Каналы, чаты и отправка",
            "Читайте каналы и чаты, ищите сообщения и отправляйте текст или файлы от вашего имени.",
            "#26A5E4", "Коммуникации", null, listOf("Чаты", "Каналы", "Отправка"), "telegram.org",
        ),
        ConnCatalogEntry(
            "x", "X", "X Corp.", "Посты, треды и поиск",
            "Ищите посты, читайте треды, публикуйте текст и собирайте ленту по интересующим темам.",
            "ink", "Соцсети", null, listOf("Поиск", "Публикация", "Треды"), "x.com",
        ),
        ConnCatalogEntry(
            "ig", "Instagram", "Meta", "Профиль, публикации и статистика",
            "Смотрите публикации и комментарии, готовьте описания и планируйте контент для аккаунта.",
            "#E4405F", "Соцсети", null, listOf("Публикации", "Комментарии", "Статистика"), "instagram.com",
        ),
        ConnCatalogEntry(
            "yt", "YouTube", "Google", "Видео, плейлисты и субтитры",
            "Ищите видео, читайте описания и субтитры, собирайте плейлисты и вытаскивайте тезисы из роликов.",
            "#FF0000", "Соцсети", null, listOf("Поиск", "Субтитры", "Плейлисты"), "youtube.com",
        ),
        ConnCatalogEntry(
            "st", "Stripe", "Stripe", "Платежи, клиенты и подписки",
            "Смотрите платежи и подписки, ищите клиентов, создавайте ссылки на оплату и разбирайте возвраты.",
            "#635BFF", "Платежи", null, listOf("Платежи", "Подписки", "Клиенты"), "stripe.com",
        ),
        ConnCatalogEntry(
            "pp", "PayPal", "PayPal", "Транзакции и счета",
            "Проверяйте транзакции, создавайте счета и сверяйте выплаты с бухгалтерией.",
            "#003087", "Платежи", null, listOf("Транзакции", "Счета", "Выплаты"), "paypal.com",
        ),
        ConnCatalogEntry(
            "sh", "Shopify", "Shopify", "Товары, заказы и клиенты",
            "Обновляйте карточки товаров, смотрите заказы и остатки, собирайте сводки по продажам.",
            "#95BF47", "Платежи", null, listOf("Товары", "Заказы", "Остатки"), "shopify.com",
        ),
        ConnCatalogEntry(
            "hs", "HubSpot", "HubSpot", "CRM, сделки и контакты",
            "Ищите контакты и компании, обновляйте сделки, добавляйте заметки и собирайте воронку.",
            "#FF7A59", "Платежи", null, listOf("Контакты", "Сделки", "Заметки"), "hubspot.com",
        ),
        ConnCatalogEntry(
            "sf", "Salesforce", "Salesforce", "Аккаунты, лиды и возможности",
            "Работайте с аккаунтами и лидами, обновляйте возможности и выгружайте отчёты по продажам.",
            "#00A1E0", "Платежи", null, listOf("Аккаунты", "Лиды", "Отчёты"), "salesforce.com",
        ),
        ConnCatalogEntry(
            "zm", "Zoom", "Zoom", "Встречи, записи и расшифровки",
            "Создавайте встречи, смотрите записи и расшифровки, отправляйте ссылки участникам.",
            "#2D8CFF", "Коммуникации", null, listOf("Встречи", "Записи", "Расшифровки"), "zoom.us",
        ),
        ConnCatalogEntry(
            "in", "Intercom", "Intercom", "Поддержка и диалоги",
            "Читайте диалоги поддержки, ищите похожие обращения и отвечайте клиентам с подсказками из базы знаний.",
            "#1F8DED", "Коммуникации", null, listOf("Диалоги", "Клиенты", "Статьи"), "intercom.com",
        ),
        ConnCatalogEntry(
            "zd", "Zendesk", "Zendesk", "Тикеты и база знаний",
            "Ищите тикеты, обновляйте статусы, добавляйте внутренние заметки и предлагайте ответы из статей.",
            "#03363D", "Коммуникации", null, listOf("Тикеты", "Статусы", "Статьи"), "zendesk.com",
        ),
        ConnCatalogEntry(
            "pg", "PostgreSQL", "PostgreSQL", "SQL-запросы к вашей базе",
            "Подключайтесь к базе только для чтения, исследуйте схему, выполняйте запросы и стройте отчёты.",
            "#4169E1", "Данные", "Новинка", listOf("Схема", "SELECT", "Отчёты"), "postgresql.org",
        ),
        ConnCatalogEntry(
            "sb", "Supabase", "Supabase", "Таблицы, storage и edge-функции",
            "Смотрите таблицы и строки, работайте с файлами и вызывайте edge-функции проекта.",
            "#3FCF8E", "Данные", "Новинка", listOf("Таблицы", "Storage", "Функции"), "supabase.com",
        ),
        ConnCatalogEntry(
            "vc", "Vercel", "Vercel", "Деплои, логи и проекты",
            "Смотрите деплои и их статусы, читайте логи сборки и запускайте повторный деплой.",
            "ink", "Разработка", "Новинка", listOf("Деплои", "Логи", "Домены"), "vercel.com",
        ),
        ConnCatalogEntry(
            "sn", "Sentry", "Sentry", "Ошибки, релизы и трейсы",
            "Ищите ошибки по проекту и релизу, читайте стектрейсы и связывайте инциденты с коммитами.",
            "#362D59", "Разработка", "Новинка", listOf("Ошибки", "Релизы", "Трейсы"), "sentry.io",
        ),
        ConnCatalogEntry(
            "td", "Todoist", "Doist", "Задачи, проекты и сроки",
            "Добавляйте задачи, ставьте сроки, разбивайте на подзадачи и закрывайте выполненные.",
            "#E44332", "Продуктивность", "Новинка", listOf("Задачи", "Сроки", "Проекты"), "todoist.com",
        ),
        ConnCatalogEntry(
            "cu", "ClickUp", "ClickUp", "Задачи, документы и цели",
            "Работайте со списками и задачами, обновляйте статусы и подтягивайте документы пространства.",
            "#7B68EE", "Продуктивность", "Новинка", listOf("Задачи", "Списки", "Документы"), "clickup.com",
        ),
        ConnCatalogEntry(
            "mi", "Miro", "Miro", "Доски, стикеры и схемы",
            "Читайте доски, добавляйте стикеры и заметки, собирайте схемы и суммаризируйте воркшопы.",
            "#FFD02F", "Дизайн", "Новинка", listOf("Доски", "Стикеры", "Схемы"), "miro.com",
        ),
        ConnCatalogEntry(
            "pic", "Picsart", "Picsart", "Изображения и быстрый редизайн",
            "Готовьте изображения для постов, меняйте фон и текст, экспортируйте готовые креативы.",
            "#C209C1", "Дизайн", "Новинка", listOf("Изображения", "Фон", "Экспорт"), "picsart.com",
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): ConnCatalogEntry? = byId[id]
    val browserEndpoints = mapOf(
        "nt" to "https://mcp.notion.com/mcp", "ln" to "https://mcp.linear.app/mcp", "sn" to "https://mcp.sentry.dev/mcp",
        "cv" to "https://mcp.canva.com/mcp", "st" to "https://mcp.stripe.com", "vc" to "https://mcp.vercel.com",
        "mi" to "https://mcp.miro.com/", "in" to "https://mcp.intercom.com/mcp", "pp" to "https://mcp.paypal.com/mcp",
        "td" to "https://ai.todoist.net/mcp", "cu" to "https://mcp.clickup.com/mcp",
        "jr" to "https://mcp.atlassian.com/v1/mcp", "cf" to "https://mcp.atlassian.com/v1/mcp",
    )
    private val otherEndpoints = mapOf("gh" to "https://api.githubcopilot.com/mcp/", "fg" to "https://mcp.figma.com/mcp",
        "gm" to "https://gmailmcp.googleapis.com/mcp/v1", "gd" to "https://drivemcp.googleapis.com/mcp/v1",
        "gc" to "https://calendarmcp.googleapis.com/mcp/v1", "gdocs" to "https://docsmcp.googleapis.com/mcp/v1",
        "gs" to "https://sheetsmcp.googleapis.com/mcp/v1", "sl" to "https://mcp.slack.com/mcp",
        "as" to "https://mcp.asana.com/v2/mcp", "hs" to "https://mcp.hubspot.com/")
    fun endpoint(id: String) = browserEndpoints[id] ?: otherEndpoints[id]
    fun browserLimit(id: String): String? = when (id) {
        "fg" -> "Figma разрешает подключение только одобренным клиентам."
        "gd", "gc", "gdocs", "gs", "gsl" -> "Для входа через Google нужен зарегистрированный OAuth-клиент Clodex."
        "sl", "as", "hs" -> "Для входа через браузер нужен зарегистрированный OAuth-клиент этого сервиса."
        else -> null
    }

    fun inCategory(entry: ConnCatalogEntry, category: String): Boolean = when (category) {
        "Все" -> true
        "Популярные" -> entry.badge == "Популярный"
        else -> entry.category == category
    }
}
