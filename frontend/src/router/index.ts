import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/anchor'
  },
  {
    path: '/anchor',
    name: 'Anchor',
    component: () => import('../views/Anchor.vue')
  },
  {
    path: '/route',
    name: 'Route',
    component: () => import('../views/Route.vue')
  },
  {
    path: '/adapt',
    name: 'Adapt',
    component: () => import('../views/Adapt.vue')
  },
  {
    path: '/group-binding',
    name: 'GroupBind',
    component: () => import('../views/GroupBind.vue')
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('../views/Logs.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
