import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/index'
import MainLayout from '@/components/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { guest: true }
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/projects' },
        { path: 'projects', name: 'Projects', component: () => import('@/views/Projects.vue') },
        { path: 'projects/:id/reviews', name: 'Reviews', component: () => import('@/views/Reviews.vue') },
        { path: 'projects/:id/statistics', name: 'Statistics', component: () => import('@/views/Statistics.vue') },
        { path: 'reviews/:id', name: 'ReviewDetail', component: () => import('@/views/ReviewDetail.vue') },
        { path: 'rules', name: 'Rules', component: () => import('@/views/Rules.vue') },
        { path: 'settings', name: 'Settings', component: () => import('@/views/Settings.vue') },
        { path: ':pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/NotFound.vue') }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = getToken()
  if (to.meta.guest) {
    next()
  } else if (!token) {
    next('/login')
  } else {
    next()
  }
})

export default router
