<!-- views/AuthView.vue -->
<template>
  <div class="auth-container">
    <el-card class="auth-card">
      <template #header>
        <div class="auth-header">
          <h2>{{ isLogin ? '登录' : '注册' }}</h2>
          <el-switch
            v-model="isLogin"
            active-text="登录"
            inactive-text="注册"
            @change="resetForm"
          />
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item v-if="!isLogin" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>

        <div class="auth-error" v-if="authStore.error">
          {{ authStore.error }}
        </div>

        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="authStore.isLoading"
            style="width: 100%"
          >
            {{ isLogin ? '登录' : '注册' }}
          </el-button>
        </el-form-item>

        <div class="auth-toggle">
          <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
          <el-button type="text" @click="isLogin = !isLogin">
            {{ isLogin ? '立即注册' : '立即登录' }}
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store'
import type { LoginCredentials, RegisterCredentials } from '../api'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const isLogin = ref(true)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (rule: any, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules = computed(() => {
  const baseRules = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, message: '密码长度至少为 6 个字符', trigger: 'blur' },
    ],
  }

  if (!isLogin.value) {
    return {
      ...baseRules,
      confirmPassword: [
        { required: true, message: '请再次输入密码', trigger: 'blur' },
        { validator: validateConfirmPassword, trigger: 'blur' },
      ],
    }
  }

  return baseRules
})

const resetForm = () => {
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  authStore.error = null
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      if (isLogin.value) {
        // 登录逻辑
        const credentials: LoginCredentials = {
          username: form.username,
          password: form.password,
        }
        const result = await authStore.login(credentials.username, credentials.password)
        if (result) {
          ElMessage.success('登录成功')
          router.push('/')
        }
      } else {
        // 注册逻辑
        const credentials: RegisterCredentials = {
          username: form.username,
          password: form.password,
        }
        const result = await authStore.register(credentials.username, credentials.password)
        if (result) {
          ElMessage.success('注册成功，请登录')
          isLogin.value = true
          resetForm()
        }
      }
    } catch (error) {
      console.error('操作失败', error)
    }
  })
}
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7fa;
}

.auth-card {
  width: 100%;
  max-width: 400px;
}

.auth-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.auth-toggle {
  margin-top: 16px;
  text-align: center;
}

.auth-error {
  color: #f56c6c;
  margin-bottom: 15px;
  font-size: 14px;
}
</style>
