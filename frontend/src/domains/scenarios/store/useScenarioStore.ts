import { create } from 'zustand'
import { api } from '@/shared/lib/api'
import type { Scenario, ScenarioParams } from '@/shared/lib/api-types'

interface ScenarioState {
    scenarios: Scenario[]
    loading: boolean
    error: string | null
    selectedIds: string[]
    load: () => Promise<void>
    create: (params: ScenarioParams) => Promise<Scenario[]>
    deleteScenario: (id: string) => Promise<void>
    toggleSelected: (id: string) => void
    clear: () => void  // ✅ Добавить
}

export const useScenarioStore = create<ScenarioState>((set, get) => ({
    scenarios: [],
    loading: false,
    error: null,
    selectedIds: [],

    async load() {
        set({ loading: true, error: null })
        try {
            const scenarios = await api.getScenarios()
            set({
                scenarios,
                loading: false,
                selectedIds: scenarios.slice(0, 2).map(s => s.id),
            })
        } catch (e) {
            set({
                loading: false,
                error: e instanceof Error ? e.message : 'Не удалось загрузить сценарии',
            })
        }
    },

    async create(params) {
        set({ loading: true, error: null })
        try {
            const scenarios = await api.createScenario(params)
            set((s) => ({
                scenarios: [...scenarios, ...s.scenarios],
                loading: false,
                selectedIds: [...s.selectedIds, ...scenarios.map(sc => sc.id)],
            }))
            return scenarios
        } catch (e) {
            set({
                loading: false,
                error: e instanceof Error ? e.message : 'Ошибка создания сценария',
            })
            throw e
        }
    },

    async deleteScenario(id: string) {
        set({ loading: true, error: null })
        try {
            await api.deleteScenario(id)
            set((s) => ({
                scenarios: s.scenarios.filter(sc => sc.id !== id),
                selectedIds: s.selectedIds.filter(sid => sid !== id),
                loading: false,
            }))
        } catch (e) {
            set({
                loading: false,
                error: e instanceof Error ? e.message : 'Ошибка удаления сценария',
            })
        }
    },

    toggleSelected(id: string) {
        set((s) => ({
            selectedIds: s.selectedIds.includes(id)
                ? s.selectedIds.filter(x => x !== id)
                : [...s.selectedIds, id],
        }))
    },

    // ✅ Очистка всех сценариев
    clear: () => set({
        scenarios: [],
        loading: false,
        error: null,
        selectedIds: [],
    }),
}))