import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/projects'
    },
    {
      path: '/projects',
      name: 'Projects',
      component: () => import('@/views/Projects.vue')
    },
    {
      path: '/projects/:id/reviews',
      name: 'Reviews',
      component: () => import('@/views/Reviews.vue')
    },
    {
      path: '/reviews/:id',
      name: 'ReviewDetail',
      component: () => import('@/views/ReviewDetail.vue')
    },
    {
      path: '/rules',
      name: 'Rules',
      component: () => import('@/views/Rules.vue')
    },
    {
      path: '/settings',
      name: 'Settings',
      component: () => import('@/views/Settings.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/NotFound.vue')
    }
  ]
})

export default router
