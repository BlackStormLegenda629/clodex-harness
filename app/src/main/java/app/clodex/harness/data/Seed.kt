package app.clodex.harness.data

/** Only the built-in skills and the connector catalog. No chats, projects, sessions or artifacts. */
object Seed {
    suspend fun seedCatalog(db: HarnessDb, resetConnectors: Boolean = false) {
        val skills = listOf(
            SkillEntity("pdf", "Работа с PDF", "Чтение, создание и заполнение PDF", Src.BUILTIN, true, "Docp", sortKey = 0),
            SkillEntity("xlsx", "Таблицы xlsx", "Формулы, графики и очистка данных", Src.BUILTIN, true, "Stack", sortKey = 1),
            SkillEntity("docx", "Документы docx", "Отчёты и письма в Word", Src.BUILTIN, true, "File", sortKey = 2),
            SkillEntity("front", "Фронтенд-дизайн", "Аккуратные интерфейсы без шаблонщины", Src.BUILTIN, false, "Code", sortKey = 3),
        )
        skills.forEach { db.skills().upsert(it) }

        ConnectorCatalog.all.forEachIndexed { i, e ->
            val old = if (resetConnectors) null else db.connectors().get(e.id)
            db.connectors().upsert(
                ConnectorEntity(
                    id = e.id, name = e.name, developer = e.developer, description = e.description,
                    color = e.color, status = old?.status ?: ConStatus.NEW, sortKey = i.toLong(),
                ),
            )
        }
    }

    /** A fresh install starts on one empty chat. */
    suspend fun ensureChat(db: HarnessDb): Long {
        val first = db.chats().getNewest()
        return first?.id ?: db.chats().insert(ChatEntity(title = "Новый чат", updatedAt = System.currentTimeMillis()))
    }
}
