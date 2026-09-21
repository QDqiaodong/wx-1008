import axios, { type AxiosError, type AxiosResponse } from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res.data
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

const get = <T>(url: string) => request.get<T>(url) as unknown as Promise<T>
const post = <T>(url: string, data?: unknown) => request.post<T>(url, data) as unknown as Promise<T>
const put = <T>(url: string, data?: unknown) => request.put<T>(url, data) as unknown as Promise<T>
const del = (url: string) => request.delete(url) as unknown as Promise<void>

export interface Anchor {
  id: number
  anchorCode: string
  maxWeight: number
  minWindSpeed: number
  maxWindSpeed: number
  locationDesc: string
  status: number
  createTime: string
  updateTime: string
}

export interface AnchorDTO {
  anchorCode: string
  maxWeight: number
  minWindSpeed: number
  maxWindSpeed: number
  locationDesc: string
}

export interface FlightRoute {
  id: number
  routeCode: string
  routeName: string
  routeGroup: string
  windSpeed: number
  windLevel: string
  description: string
  status: number
  createTime: string
  updateTime: string
}

export interface RouteDTO {
  routeCode: string
  routeName: string
  routeGroup: string
  windSpeed: number
  description: string
}

export interface AdaptResult {
  valid: boolean
  routeId: number
  anchorId: number
  bindId: number
  reason: string
  rebindCount: number
  unbindCount: number
  logIds: number[]
}

export interface RouteAnchor {
  id: number
  routeId: number
  anchorId: number
  bindTime: string
  unbindTime: string
  status: number
}

export interface AdaptLog {
  id: number
  routeId: number
  routeCode: string
  anchorId: number
  anchorCode: string
  operationType: string
  beforeWindSpeed: number
  afterWindSpeed: number
  beforeWeight: number
  afterWeight: number
  reason: string
  operator: string
  createTime: string
}

export interface GroupAnchorResult {
  anchorId: number
  anchorCode: string
  maxWeight: number
  minWindSpeed: number
  maxWindSpeed: number
  eligible: boolean
  failedChecks: string[]
  reasons: string[]
  occupiedByRouteCode: string | null
}

export interface GroupRehearseResult {
  routeId: number
  routeCode: string
  routeName: string
  routeWindSpeed: number
  windLevel: string
  requiredMinWeight: number
  totalCount: number
  eligibleCount: number
  rejectedCount: number
  eligibleTotalWeight: number
  requiredTotalWeight: number
  totalWeightBudgetOk: boolean
  groupValid: boolean
  policy: string
  policyNotice: string
  anchorResults: GroupAnchorResult[]
  weightRuleTable: Record<string, number>
}

export interface GroupSubmitResult {
  routeId: number
  routeCode: string
  committed: boolean
  bindIds: number[]
  submittedCount: number
  rehearsal: GroupRehearseResult
  message: string
  logIds: number[]
}

export const anchorApi = {
  list: () => get<Anchor[]>('/anchor'),
  get: (id: number) => get<Anchor>(`/anchor/${id}`),
  create: (data: AnchorDTO) => post<Anchor>('/anchor', data),
  update: (id: number, data: AnchorDTO) => put<Anchor>(`/anchor/${id}`, data),
  delete: (id: number) => del(`/anchor/${id}`),
  filter: (minWind: number, maxWind: number) => get<Anchor[]>(`/anchor/filter?minWind=${minWind}&maxWind=${maxWind}`)
}

export const routeApi = {
  list: () => get<FlightRoute[]>('/route'),
  get: (id: number) => get<FlightRoute>(`/route/${id}`),
  create: (data: RouteDTO) => post<FlightRoute>('/route', data),
  update: (id: number, data: RouteDTO) => put<FlightRoute>(`/route/${id}`, data),
  delete: (id: number) => del(`/route/${id}`),
  groups: () => get<string[]>('/route/groups')
}

export const adaptApi = {
  bind: (routeId: number, anchorId: number) => post<AdaptResult>('/adapt/bind', { routeId, anchorId }),
  unbind: (routeId: number, anchorId: number) => post<AdaptResult>('/adapt/unbind', { routeId, anchorId }),
  check: (routeId: number, anchorId: number) => get<AdaptResult>(`/adapt/check?routeId=${routeId}&anchorId=${anchorId}`),
  recheck: (routeId: number) => post<AdaptResult>(`/adapt/recheck/${routeId}`),
  logs: (routeId?: number, anchorId?: number) => {
    let url = '/adapt/logs'
    if (routeId) url += `?routeId=${routeId}`
    else if (anchorId) url += `?anchorId=${anchorId}`
    return get<AdaptLog[]>(url)
  },
  bound: (routeId: number) => get<RouteAnchor[]>(`/adapt/bound/${routeId}`)
}

export const groupBindingApi = {
  rules: () => get<Record<string, number>>('/group-binding/rules'),
  rehearse: (routeId: number, anchorIds: number[], operator?: string) =>
    post<GroupRehearseResult>('/group-binding/rehearse', { routeId, anchorIds, operator }),
  submit: (routeId: number, anchorIds: number[], operator?: string) =>
    post<GroupSubmitResult>('/group-binding/submit', { routeId, anchorIds, operator })
}
