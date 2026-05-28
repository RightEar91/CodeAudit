import { ref } from 'vue'
import { getToken as getStoredToken, setToken as setStoredToken, removeToken as removeStoredToken } from '@/api/index'

const token = ref<string | null>(getStoredToken())

export function useAuth() {
    function login(tokenValue: string) {
        setStoredToken(tokenValue)
        token.value = tokenValue
    }

    function logout() {
        removeStoredToken()
        token.value = null
    }

    function isLoggedIn() {
        return token.value !== null
    }

    return { token, login, logout, isLoggedIn }
}
