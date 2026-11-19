import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import DashboardView from '@/views/DashboardView.vue'
import AlertsIncidentsView from '@/views/AlertsIncidentsView.vue'
import IncidentDetailView from '@/views/IncidentDetailView.vue'
import ReviewsView from '@/views/ReviewsView.vue'
import EvaluationView from '@/views/EvaluationView.vue'
import SystemStatusView from '@/views/SystemStatusView.vue'

/** 业务页面挂在 AppLayout 下；系统状态页独立。 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '安全态势总览' } },
        { path: 'alerts', name: 'alerts', component: AlertsIncidentsView, meta: { title: '告警与事件' } },
        {
          path: 'incidents/:id',
          name: 'incident-detail',
          component: IncidentDetailView,
          meta: { title: '事件详情与研判' },
        },
        { path: 'reviews', name: 'reviews', component: ReviewsView, meta: { title: '审核记录' } },
        { path: 'evaluation', name: 'evaluation', component: EvaluationView, meta: { title: '效果评估' } },
      ],
    },
    { path: '/status', name: 'status', component: SystemStatusView },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

export default router
