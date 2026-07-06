import * as XLSX from 'xlsx'
import type {
    BusinessGraph,
    FactRow,
    GraphNodeModel,
    GraphOperator,
} from '@/shared/lib/api-types'

/** Читаемое представление операторов в формулах. */
const OPERATOR_LABEL: Record<GraphOperator, string> = {
    '*': '×',
    '/': '÷',
    '-': '−',
    '+': '+',
    '%': '%',
}

function operatorLabel(op: GraphOperator): string {
    return OPERATOR_LABEL[op] ?? op
}

export interface ExportOptions {
    facts: FactRow[]
    graph: BusinessGraph
    indicatorFilter?: string
    includeFormulaSheet: boolean
}

export interface ExportSummary {
    rows: number
    sheets: string[]
}

/** Очищает id узла от префиксов и суффиксов */
function cleanNodeId(id: string): string {
    // Убираем ind- в начале
    let cleaned = id.replace(/^ind-/, '')
    // Убираем суффикс -число в конце
    cleaned = cleaned.replace(/-\d+$/, '')
    return cleaned
}

/** Строит строку-формулу для узла из входящих рёбер */
function buildFormulaForTarget(
    targetId: string,
    graph: BusinessGraph,
    nodeMap: Map<string, GraphNodeModel>
): string | null {
    const incoming = graph.edges.filter((e) => e.target === targetId)
    if (incoming.length === 0) return null

    const targetNode = nodeMap.get(targetId)
    const targetName = targetNode?.indicator || cleanNodeId(targetId)

    const parts: string[] = []
    incoming.forEach((edge, idx) => {
        const sourceNode = nodeMap.get(edge.source)
        const sourceName = sourceNode?.indicator || cleanNodeId(edge.source)
        if (idx > 0) parts.push(operatorLabel(edge.operator))
        parts.push(sourceName)
    })

    return `${targetName} = ${parts.join(' ')}`
}

export function exportToExcel(opts: ExportOptions): ExportSummary {
    const { facts, graph, indicatorFilter, includeFormulaSheet } = opts

    const wb = XLSX.utils.book_new()
    const sheets: string[] = []

    // ---------- Лист «Данные» ----------
    const filtered = indicatorFilter
        ? facts.filter((f) => f.indicator === indicatorFilter)
        : facts

    const dataRows = filtered.map((f) => ({
        'Отчётный период': f.period,
        'Федеральный округ РФ': f.district,
        'Субъект РФ': f.subject,
        Показатель: f.indicator,
        'Мера измерения': f.unit,
        Значение: f.value,
    }))

    const dataSheet = XLSX.utils.json_to_sheet(dataRows, {
        header: [
            'Отчётный период',
            'Федеральный округ РФ',
            'Субъект РФ',
            'Показатель',
            'Мера измерения',
            'Значение',
        ],
    })
    dataSheet['!cols'] = [{ wch: 16 }, { wch: 22 }, { wch: 18 }, { wch: 28 }, { wch: 16 }, { wch: 14 }]
    XLSX.utils.book_append_sheet(wb, dataSheet, 'Данные')
    sheets.push('Данные')

    // ---------- Лист «Формула» ----------
    if (includeFormulaSheet) {
        const aoa: (string | number)[][] = []

        const nodeMap = new Map(graph.nodes.map((n) => [n.id, n]))

        // ✅ Проверяем, есть ли рёбра в графе
        const hasEdges = graph.edges.length > 0
        const hasNodes = graph.nodes.length > 0

        if (!hasNodes || !hasEdges) {
            aoa.push(['📊 Граф бизнес-модели'])
            aoa.push([])
            aoa.push(['Граф пуст.'])
            aoa.push(['Для отображения формул:'])
            aoa.push(['1. Перейдите на вкладку «Граф бизнес-модели»'])
            aoa.push(['2. Нажмите «Регенерация» для построения графа из показателей'])
            aoa.push(['3. Соедините узлы рёбрами с операторами'])
            aoa.push(['4. Нажмите «Сохранить» для отправки формул на сервер'])
        } else {
            // ---------- РАЗДЕЛ 1: ФОРМУЛЫ ----------
            aoa.push(['📊 ФОРМУЛЫ РАСЧЁТА'])
            aoa.push([])

            // Находим все узлы, у которых есть входящие рёбра (цели)
            const targets = new Set(graph.edges.map((e) => e.target))
            const formulas: string[] = []

            for (const targetId of targets) {
                const formula = buildFormulaForTarget(targetId, graph, nodeMap)
                if (formula) formulas.push(formula)
            }

            if (formulas.length === 0) {
                aoa.push(['Нет формул. Соедините узлы рёбрами.'])
            } else {
                formulas.forEach((f) => aoa.push([f]))
            }
            aoa.push([])

            // ---------- РАЗДЕЛ 2: УЗЛЫ ----------
            aoa.push(['📋 УЗЛЫ ГРАФА'])
            aoa.push([])
            aoa.push(['Показатель', 'Значение', 'Ед. изм.', 'Тип'])

            // Сначала показываем узлы с indicator
            const nodesWithIndicator = graph.nodes.filter((n) => n.indicator && n.indicator.trim() !== '')
            const nodesWithoutIndicator = graph.nodes.filter((n) => !n.indicator || n.indicator.trim() === '')

            nodesWithIndicator.forEach((n) => {
                const value = n.currentValue ?? 0
                aoa.push([
                    n.indicator,
                    typeof value === 'number' ? value : 0,
                    n.unit || '—',
                    n.isDerived ? 'Производный' : 'Источник',
                ])
            })

            // Если есть узлы без индикатора, показываем их очищенные id
            if (nodesWithoutIndicator.length > 0) {
                nodesWithoutIndicator.forEach((n) => {
                    const name = cleanNodeId(n.id)
                    aoa.push([
                        name,
                        n.currentValue ?? 0,
                        n.unit || '—',
                        n.isDerived ? 'Производный' : 'Источник',
                    ])
                })
            }
            aoa.push([])

            // ---------- РАЗДЕЛ 3: СВЯЗИ ----------
            if (hasEdges) {
                aoa.push(['🔗 СВЯЗИ ГРАФА'])
                aoa.push([])
                aoa.push(['Из', 'Оператор', 'В'])

                graph.edges.forEach((e) => {
                    const sourceNode = nodeMap.get(e.source)
                    const targetNode = nodeMap.get(e.target)

                    const from = sourceNode?.indicator || cleanNodeId(e.source)
                    const to = targetNode?.indicator || cleanNodeId(e.target)

                    aoa.push([from, operatorLabel(e.operator), to])
                })
            }
        }

        const formulaSheet = XLSX.utils.aoa_to_sheet(aoa)
        formulaSheet['!cols'] = [{ wch: 40 }, { wch: 16 }, { wch: 40 }]
        XLSX.utils.book_append_sheet(wb, formulaSheet, 'Формула')
        sheets.push('Формула')
    }

    const suffix = indicatorFilter ? sanitizeName(indicatorFilter) : 'all'
    const wbout = XLSX.write(wb, {
        bookType: 'xlsx',
        type: 'array',
        bookSST: false,
    })

    const blob = new Blob([wbout], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8',
    })
    downloadBlob(blob, `Планирование_доходов_${suffix}.xlsx`)

    return { rows: dataRows.length, sheets }
}

/* ---------- CSV-выгрузка ---------- */

function csvCell(value: string | number): string {
    const s = String(value ?? '')
    return /[";\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function exportToCsv(opts: Omit<ExportOptions, 'includeFormulaSheet' | 'graph'>): ExportSummary {
    const { facts, indicatorFilter } = opts
    const filtered = indicatorFilter
        ? facts.filter((f) => f.indicator === indicatorFilter)
        : facts

    const headers = [
        'Отчётный период',
        'Федеральный округ РФ',
        'Субъект РФ',
        'Показатель',
        'Мера измерения',
        'Значение',
    ]

    const lines = [headers.join(';')]
    for (const f of filtered) {
        lines.push(
            [f.period, f.district, f.subject, f.indicator, f.unit, f.value].map(csvCell).join(';'),
        )
    }

    const csv = '\uFEFF' + lines.join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const suffix = indicatorFilter ? sanitizeName(indicatorFilter) : 'all'
    downloadBlob(blob, `Планирование_доходов_${suffix}.csv`)

    return { rows: filtered.length, sheets: ['Данные'] }
}

function downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
}

function sanitizeName(name: string): string {
    return name.replace(/[\\/:*?"<>|]+/g, '').trim().replace(/\s+/g, '_')
}