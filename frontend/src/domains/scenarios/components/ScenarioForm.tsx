import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Sparkles } from 'lucide-react'
import {
    Button,
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    Select,
    Input,
    Field,
} from '@/shared/ui'
import { useDatasetStore } from '@/shared/store/useDatasetStore'
import { useScenarioStore } from '../store/useScenarioStore'
import type { CalcMethod, ScenarioParams } from '@/shared/lib/api-types'

const FORECAST_MODE_LABELS: Record<string, string> = {
    best: 'Лучшая модель (авто)',
    all: 'Все модели',
    sarimax: 'SARIMAX',
    prophet: 'Prophet',
    exponential_smoothing: 'Экспоненциальное сглаживание',
    stl: 'STL-декомпозиция',
    ridge: 'Гребневая регрессия',
    croston: 'Кростон (для редких событий)',
}

const schema = z
    .object({
        name: z.string().trim().min(1, 'Введите название плана').max(60, 'Слишком длинное название'),
        targetIndicator: z.string().min(1, 'Выберите показатель'),
        periodMode: z.enum(['max', 'half', 'custom']),
        periodFrom: z.coerce.number().optional(),
        periodTo: z.coerce.number().optional(),
        method: z.enum(['growth-rate', 'avg-3m', 'avg-6m']),
        forecastMode: z.enum(['best', 'all', 'sarimax', 'prophet', 'exponential_smoothing', 'stl', 'ridge', 'croston']),
    })
    .refine(
        (data) => {
            if (data.periodMode === 'custom') {
                return data.periodFrom !== undefined && data.periodTo !== undefined && data.periodTo >= data.periodFrom
            }
            return true
        },
        {
            message: 'Конец периода должен быть не меньше начала',
            path: ['periodTo'],
        }
    )

type FormValues = z.infer<typeof schema>

const METHOD_OPTIONS: { value: CalcMethod; label: string }[] = [
    { value: 'growth-rate', label: 'По темпам роста' },
    { value: 'avg-3m', label: 'Среднее за 3 мес' },
    { value: 'avg-6m', label: 'Среднее за 6 мес' },
]

const DEFAULT_INDICATOR = 'Доход банка'

interface ScenarioFormProps {
    onSuccess?: () => void
    onCancel?: () => void
}

export function ScenarioForm({ onSuccess, onCancel }: ScenarioFormProps) {
    const { indicators, periods, loadFacts } = useDatasetStore()
    const { create, loading: scenarioLoading } = useScenarioStore()

    useEffect(() => {
        loadFacts()
    }, [])

    const lastPeriod = periods.length > 0 ? periods[periods.length - 1] : null
    const maxHorizon = 24

    const {
        register,
        handleSubmit,
        watch,
        setValue,
        formState: { errors, isSubmitting },
        reset,
    } = useForm<FormValues>({
        resolver: zodResolver(schema),
        defaultValues: {
            name: '',
            targetIndicator: DEFAULT_INDICATOR,
            periodMode: 'half',
            method: 'avg-3m',
            forecastMode: 'best',
            periodFrom: 1,
            periodTo: 6,
        },
    })

    const periodMode = watch('periodMode')
    const isCustom = periodMode === 'custom'

    const getPeriodRange = (mode: string): { from: number; to: number } => {
        switch (mode) {
            case 'max':
                return { from: 1, to: maxHorizon }
            case 'half':
                return { from: 1, to: Math.floor(maxHorizon / 2) }
            default:
                return { from: 1, to: 6 }
        }
    }

    useEffect(() => {
        if (periodMode !== 'custom') {
            const range = getPeriodRange(periodMode)
            setValue('periodFrom', range.from)
            setValue('periodTo', range.to)
        }
    }, [periodMode, setValue])

    const onSubmit = async (values: FormValues) => {
        const params: ScenarioParams = {
            name: values.name,
            targetIndicator: values.targetIndicator,
            periodFrom: values.periodFrom || 1,
            horizonMonths: values.periodTo || 6,
            method: values.method,
            seasonality: true,
            forecastMode: values.forecastMode,
        }
        await create(params)
        reset()
        onSuccess?.()
    }

    const busy = isSubmitting || scenarioLoading

    return (
        <Card className="border-accent-lime/30">
            <CardHeader>
                <CardTitle>Новый вариант плана</CardTitle>
            </CardHeader>
            <CardBody>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                        <Field label="Название плана" htmlFor="name" error={errors.name?.message}>
                            <Input id="name" placeholder="Напр. «Базовый Q3»" {...register('name')} />
                        </Field>

                        <Field label="Целевой показатель" htmlFor="targetIndicator" error={errors.targetIndicator?.message}>
                            <Select id="targetIndicator" {...register('targetIndicator')}>
                                {indicators.length === 0 ? (
                                    <option value={DEFAULT_INDICATOR}>{DEFAULT_INDICATOR}</option>
                                ) : (
                                    indicators.map((ind) => (
                                        <option key={ind} value={ind}>
                                            {ind}
                                        </option>
                                    ))
                                )}
                            </Select>
                        </Field>
                    </div>

                    <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                        <Field
                            label="Метод расчёта"
                            htmlFor="method"
                            error={errors.method?.message}
                        >
                            <Select id="method" {...register('method')}>
                                {METHOD_OPTIONS.map((m) => (
                                    <option key={m.value} value={m.value}>
                                        {m.label}
                                    </option>
                                ))}
                            </Select>
                        </Field>

                        <Field
                            label="Модель прогноза"
                            htmlFor="forecastMode"
                            error={errors.forecastMode?.message}
                        >
                            <Select id="forecastMode" {...register('forecastMode')}>
                                {Object.entries(FORECAST_MODE_LABELS).map(([value, label]) => (
                                    <option key={value} value={value}>{label}</option>
                                ))}
                            </Select>
                        </Field>

                        <Field
                            label="Глубина планирования"
                            htmlFor="periodMode"
                            error={errors.periodMode?.message}
                        >
                            <Select id="periodMode" {...register('periodMode')}>
                                <option value="max">Максимальная (до 24 мес)</option>
                                <option value="half">Половина от максимума</option>
                                <option value="custom">Ввести свою</option>
                            </Select>
                        </Field>

                        {isCustom && (
                            <div className="flex items-end gap-2">
                                <Field label="С" error={errors.periodFrom?.message}>
                                    <Input
                                        type="number"
                                        min={1}
                                        max={24}
                                        placeholder="1"
                                        {...register('periodFrom', { valueAsNumber: true })}
                                    />
                                </Field>
                                <span className="pb-2 text-text-muted">—</span>
                                <Field label="По" error={errors.periodTo?.message}>
                                    <Input
                                        type="number"
                                        min={1}
                                        max={24}
                                        placeholder="6"
                                        {...register('periodTo', { valueAsNumber: true })}
                                    />
                                </Field>
                            </div>
                        )}
                    </div>

                    {!isCustom && (
                        <div className="text-sm text-text-secondary">
                            {lastPeriod ? `Последний известный месяц: ${lastPeriod} → прогноз на +${getPeriodRange(periodMode).to} мес` : 'Загрузите данные'}
                        </div>
                    )}

                    {errors.periodTo && (
                        <p className="text-sm text-accent-red">{errors.periodTo.message}</p>
                    )}

                    <div className="flex justify-end gap-2">
                        {onCancel && (
                            <Button type="button" variant="secondary" onClick={onCancel}>
                                Отмена
                            </Button>
                        )}
                        <Button type="submit" disabled={busy}>
                            <Sparkles className="h-4 w-4" />
                            {busy ? 'Генерация...' : 'Сгенерировать план'}
                        </Button>
                    </div>
                </form>
            </CardBody>
        </Card>
    )
}