import { createRouter, createWebHashHistory } from 'vue-router'
import { loadSession } from './session'
import DashboardView from './views/DashboardView.vue'
import LoginView from './views/LoginView.vue'
import ReportsView from './views/ReportsView.vue'

// Hash history works on GitHub Pages (no server rewrites) and behind nginx alike.
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/reports', name: 'reports', component: ReportsView },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const user = await loadSession().catch(() => null)
  return user ? true : { name: 'login', query: { next: to.fullPath } }
})
