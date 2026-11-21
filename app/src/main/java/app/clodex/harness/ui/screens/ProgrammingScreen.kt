package app.clodex.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.Caveman
import app.clodex.harness.data.CavemanIntensity
import app.clodex.harness.data.SkillEntity
import app.clodex.harness.data.code.CodeMode
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.vm.SheetKind

private const val CAVEMAN_SOURCE = "https://github.com/JuliusBrussee/caveman"

@Composable
fun ProgrammingScreen() {
    val nav = LocalNav.current
    val vms = LocalVms.current
    val s by vms.settings.settings.collectAsStateWithLifecycle()
    val skills by vms.skills.skills.collectAsStateWithLifecycle()
    val mode by vms.code.mode.collectAsStateWithLifecycle()
    val workspace by vms.code.workspace.collectAsStateWithLifecycle()
    val spaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val configs by vms.settings.mcpConfigs.collectAsStateWithLifecycle()
    val chats by vms.chat.chats.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val grants = configs.values.flatMap { it.grants }
    val cavemanSkills = skills.filter { it.id in Caveman.skillIds }.sortedBy { Caveman.skillIds.indexOf(it.id) }

    ScreenFrame {
        Header("Программирование", onBack = { nav.pop() })
        Body {
            IntroCard()

            Sg("Caveman")
            GrpOf(
                {
                    Li {
                        IconPlate(HIcon.Code)
                        Titles("Caveman", if (s.cavemanInstalled) "Установлен · ${s.cavemanIntensity.label}" else "Не установлен")
                        Bdg(if (s.cavemanInstalled) "Вкл." else "Выкл.", if (s.cavemanInstalled) BdgKind.Blue else BdgKind.Grey)
                    }
                },
            )
            if (s.cavemanInstalled) {
                Seg(
                    CavemanIntensity.entries.map { it.label },
                    s.cavemanIntensity.label,
                    { label -> CavemanIntensity.entries.firstOrNull { it.label == label }?.let { vms.skills.setCavemanIntensity(it) } },
                    pad = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 8.dp),
                )
                if (cavemanSkills.isEmpty()) {
                    SheetNote("Навыки Caveman не найдены. Нажмите «Переустановить», чтобы вернуть пять навыков.", Modifier.padding(horizontal = 20.dp))
                } else {
                    Grp(cavemanSkills) { skill, _ ->
                        CavemanSkillRow(skill) { vms.skills.toggleCavemanSkill(skill.id) }
                    }
                }
                GrpOf(
                    {
                        Li(onClick = { uri.openUri(CAVEMAN_SOURCE) }) {
                            IconPlate(HIcon.Docp)
                            Titles("Источник · Apache 2.0", "JuliusBrussee/caveman, адаптация для Clodex")
                            Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
                        }
                    },
                    {
                        Li(onClick = { vms.skills.uninstallCaveman() }) {
                            IconPlate(HIcon.Trash, tint = H.c.dt, bg = H.c.db)
                            Text("Удалить Caveman", style = H.t.bodyMed, color = H.c.dt, modifier = Modifier.weight(1f))
                        }
                    },
                )
            } else {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Btn("Установить Caveman", { vms.skills.installCaveman() }, Modifier.fillMaxWidth(), primary = true, icon = HIcon.Dl)
                }
            }
            SheetNote(
                "Caveman добавляет к запросам Chat и Code текстовые инструкции о краткости. Он не даёт новых инструментов, разрешений или доступа к запуску команд.",
                Modifier.padding(horizontal = 20.dp),
            )

            Sg("Сохранённые разрешения")
            if (grants.isEmpty()) {
                GrpOf({ Li { Titles("Нет сохранённых разрешений", "Подтверждённые инструменты появятся здесь") } })
            } else {
                Grp(grants) { grant, _ ->
                    Li {
                        IconPlate(HIcon.Lock)
                        Titles(
                            "${grant.permission.connectorId} · ${grant.permission.tool}",
                            chats.firstOrNull { it.id == grant.chatId }?.title ?: "Чат ${grant.chatId}",
                        )
                        IconBtn(HIcon.X, "Отозвать разрешение", { vms.settings.revokeMcp(grant) })
                    }
                }
            }
            SheetNote(
                "Разрешение запоминается только для конкретного инструмента и чата. Смена подключения или инструмента требует нового согласия.",
                Modifier.padding(horizontal = 20.dp),
            )

            Sg("Рабочая область")
            GrpOf(
                {
                    Li {
                        IconPlate(HIcon.Branch)
                        Titles("Текущая область", if (workspace == "Все") "Все рабочие области" else workspace)
                    }
                },
                {
                    Li(onClick = { vms.app.open(SheetKind.Workspace) }) {
                        IconPlate(HIcon.Folder)
                        Text("Управлять областями", style = H.t.bodyMed, color = H.c.ac, modifier = Modifier.weight(1f))
                        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
                    }
                },
            )
            if (spaces.isEmpty()) {
                SheetNote("Сохранённых областей нет. Добавьте репозиторий GitHub или папку через системный выбор.", Modifier.padding(horizontal = 20.dp))
            } else {
                Grp(spaces) { space, _ ->
                    val selected = space.name == workspace
                    Li(onClick = { vms.code.selectWorkspace(space.name) }, role = Role.RadioButton) {
                        IconPlate(if (space.kind == "gh") HIcon.Branch else HIcon.Folder)
                        Titles(space.name, if (space.kind == "gh") "GitHub" else "Папка на устройстве")
                        if (selected) Ic(HIcon.Ck, tint = H.c.ac)
                    }
                }
                SheetNote(
                    "Добавление, удаление, системный выбор папки и переход к терминалу остаются в шторке «Рабочая область». Выбор области не переносит существующие сессии.",
                    Modifier.padding(horizontal = 20.dp),
                )
            }

            Sg("Новые сессии Code")
            Grp(CodeMode.entries) { choice, _ ->
                val description = when (choice) {
                    CodeMode.PLAN -> "Только чтение и план, без записи."
                    CodeMode.EDITS -> "Просмотр правок и подтверждение перед записью."
                    CodeMode.AUTO -> "Запись в папку или коммит в отдельную ветку GitHub без отдельного подтверждения."
                }
                Li(onClick = { vms.code.setMode(choice.label) }, role = Role.RadioButton) {
                    Titles(choice.label, description)
                    if (mode == choice.label) Ic(HIcon.Ck, tint = H.c.ac)
                }
            }
            SheetNote("Режим можно поменять в самой сессии.", Modifier.padding(horizontal = 20.dp))

            Sg("Модель и поиск")
            GrpOf(
                { ActionRow("Модель", s.model, s.activeProvider().name, HIcon.Plug) { vms.app.open(SheetKind.Model) } },
                { ActionRow("Уровень усилий", s.effort, icon = HIcon.Code) { vms.app.open(SheetKind.Effort) } },
                { ActionRow("Поиск в интернете", if (s.on("web")) "Вкл." else "Выкл.", icon = HIcon.Globe) { nav.settings("search") } },
            )

            Sg("Инструкции и скиллы")
            GrpOf(
                { ActionRow("Инструкции агента", icon = HIcon.Docp) { nav.settings("instr") } },
                { ActionRow("Скиллы", "${skills.count { it.enabled && it.body.isNotBlank() }} с инструкциями", icon = HIcon.Pen) { nav.settings("skills") } },
                { ActionRow("Разрешения и поведение", mode, icon = HIcon.Lock) { nav.settings("perm") } },
                { ActionRow("Рабочие папки", s.workDirs.size.toString(), icon = HIcon.Folder) { nav.settings("dirs") } },
            )
            SheetNote(
                "Включённые скиллы с непустым текстом добавляются к запросам как инструкции. Они не устанавливают плагины и не дают доступ к запуску команд.",
                Modifier.padding(horizontal = 20.dp),
            )
            SheetNote(
                "Code читает файлы и готовит изменения, но не запускает команды, сборки и тесты.",
                Modifier.padding(horizontal = 20.dp),
            )
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun IntroCard() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(HDim.card)).background(H.c.s1).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconPlate(HIcon.Code, size = 48.dp, iconSize = 24.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Настройки для Chat и Code", style = H.t.row14Semi, color = H.c.ink)
            Text(
                "Режим сессий, рабочая область, Caveman, модель и сохранённые разрешения — на одной странице.",
                style = H.t.small, color = H.c.mu,
            )
        }
    }
}

@Composable
private fun CavemanSkillRow(skill: SkillEntity, onToggle: () -> Unit) {
    Li(onClick = onToggle, role = Role.Switch) {
        IconPlate(HIcon.Pen)
        Titles(skill.name, skill.description)
        Sw(skill.enabled)
    }
}

@Composable
private fun ActionRow(title: String, value: String? = null, sub: String? = null, icon: HIcon, onClick: () -> Unit) {
    Li(onClick = onClick) {
        IconPlate(icon)
        Titles(title, sub)
        if (value != null) Text(value, style = H.t.row14, color = H.c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
}
