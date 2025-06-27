import { useState, useEffect } from 'react'
import Container from '@mui/material/Container'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import Tabs from '@mui/material/Tabs'
import Tab from '@mui/material/Tab'
import { register, login, changePassword } from './services/api'
import { z } from 'zod'

type Mode = 'login' | 'register'

const usernameRegex = /^[a-zA-Z0-9_]{3,20}$/
const passwordRegex = /^(?=.*[a-zA-Z])(?=.*\d)[a-zA-Z\d!@#$%^&*]{6,32}$/

const loginSchema = z.object({
  username: z.string()
    .min(3, '用户名长度不能少于3位')
    .max(20, '用户名不能超过20位')
    .regex(usernameRegex, '用户名只能包含字母、数字和下划线'),
  password: z.string()
    .min(6, '密码不能少于6位')
    .max(32, '密码不能超过32位')
    .regex(passwordRegex, '密码需包含字母和数字，可选特殊字符')
})

const registerSchema = loginSchema.extend({
  confirm: z.string()
}).refine(d => d.password === d.confirm, {
  message: '两次密码不一致',
  path: ['confirm']
})

const UserSchema = z.object({
  id: z.number(),
  username: z.string()
})
type User = z.infer<typeof UserSchema>

export default function AuthPage() {
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [message, setMessage] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [currentUser, setCurrentUser] = useState<User | null>(null)

  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmNewPassword, setConfirmNewPassword] = useState('')
  const [changeErrors, setChangeErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    const s = localStorage.getItem('user')
    if (s) {
      const p = UserSchema.safeParse(JSON.parse(s))
      if (p.success) {
        setCurrentUser(p.data)
      } else {
        localStorage.removeItem('user')
      }
    }
  }, [])

  const handleSubmit = async () => {
    setMessage('')
    setErrors({})

    if (mode === 'register') {
      const r = registerSchema.safeParse({ username, password, confirm })
      if (!r.success) {
        const e: Record<string, string> = {}
        r.error.errors.forEach(er => e[er.path[0] as string] = er.message)
        setErrors(e)
        return
      }

      try {
        const res = await register({ username, password })

        if (!res) {
          setMessage('注册失败：无响应')
        } else if (res.error) {
          setMessage(res.error || '注册失败：未知错误')
        } else {
          setMessage('注册成功')
        }
      } catch (err) {
        setMessage('注册失败：网络错误')
      }
    } else {
      const r = loginSchema.safeParse({ username, password })
      if (!r.success) {
        const e: Record<string, string> = {}
        r.error.errors.forEach(er => e[er.path[0] as string] = er.message)
        setErrors(e)
        return
      }

      try {
        const res = await login({ username, password })

        if (!res) {
          setMessage('登录失败：无响应')
        } else if (res.error) {
          setMessage(res.error || '登录失败：未知错误')
        } else if (res.user) {
          localStorage.setItem('user', JSON.stringify(res.user))
          setCurrentUser(res.user)
          setMessage(`欢迎，${res.user.username}`)
        } else {
          setMessage('登录失败')
        }
      } catch (err) {
        setMessage('登录失败：网络错误')
      }
    }
  }

  const handleChangePassword = async () => {
    setMessage('')
    setChangeErrors({})

    const e: Record<string, string> = {}

    if (!oldPassword) e.oldPassword = '请输入旧密码'
    if (!newPassword) e.newPassword = '请输入新密码'
    else if (!passwordRegex.test(newPassword)) e.newPassword = '新密码需包含字母和数字，可选特殊字符'
    if (confirmNewPassword !== newPassword) e.confirmNewPassword = '两次密码不一致'

    if (Object.keys(e).length > 0) {
      setChangeErrors(e)
      return
    }

    try {
      const res = await changePassword({
        username: currentUser!.username,
        oldPassword,
        newPassword
      })

      if (!res) {
        setMessage('修改失败：无响应')
      } else if (res.error) {
        setMessage(res.error || '修改失败：未知错误')
      } else {
        setMessage('修改成功')
        setOldPassword('')
        setNewPassword('')
        setConfirmNewPassword('')
      }
    } catch (err) {
      setMessage('修改失败：网络错误')
    }
  }

  const logout = () => {
    localStorage.removeItem('user')
    setCurrentUser(null)
    setUsername('')
    setPassword('')
    setConfirm('')
    setMessage('')
  }

  if (currentUser) return (
    <Container maxWidth="sm">
      <Box mt={8} display="flex" flexDirection="column" alignItems="center" gap={2}>
        <Typography variant="h5">你好，{currentUser.username}</Typography>
        <Button variant="outlined" onClick={logout}>退出登录</Button>

        <Box mt={4} width="100%" component="form" display="flex" flexDirection="column" gap={2}
          onSubmit={e => {
            e.preventDefault()
            handleChangePassword()
          }}
        >
          <Typography variant="h6">修改密码</Typography>
          <TextField
            label="旧密码"
            type="password"
            fullWidth
            value={oldPassword}
            onChange={e => setOldPassword(e.target.value)}
            error={!!changeErrors.oldPassword}
            helperText={changeErrors.oldPassword}
          />
          <TextField
            label="新密码"
            type="password"
            fullWidth
            value={newPassword}
            onChange={e => setNewPassword(e.target.value)}
            error={!!changeErrors.newPassword}
            helperText={changeErrors.newPassword}
          />
          <TextField
            label="确认新密码"
            type="password"
            fullWidth
            value={confirmNewPassword}
            onChange={e => setConfirmNewPassword(e.target.value)}
            error={!!changeErrors.confirmNewPassword}
            helperText={changeErrors.confirmNewPassword}
          />
          <Button type="submit" variant="contained">提交修改</Button>
          {message && <Typography color="error">{message}</Typography>}
        </Box>
      </Box>
    </Container>
  )

  return (
    <Container maxWidth="sm">
      <Box mt={8}>
        <Tabs value={mode} onChange={(_, v) => setMode(v as Mode)}>
          <Tab value="login" label="登录" />
          <Tab value="register" label="注册" />
        </Tabs>
        <Box component="form" mt={3} display="flex" flexDirection="column" gap={2} onSubmit={e => { e.preventDefault(); handleSubmit() }}>
          <TextField label="用户名" fullWidth value={username} onChange={e => setUsername(e.target.value)} error={!!errors.username} helperText={errors.username} />
          <TextField label="密码" type="password" fullWidth value={password} onChange={e => setPassword(e.target.value)} error={!!errors.password} helperText={errors.password} />
          {mode === 'register' &&
            <TextField label="确认密码" type="password" fullWidth value={confirm} onChange={e => setConfirm(e.target.value)} error={!!errors.confirm} helperText={errors.confirm} />
          }
          <Button type="submit" variant="contained" size="large">{mode === 'login' ? '登录' : '注册'}</Button>
          {message && <Typography color="error">{message}</Typography>}
        </Box>
      </Box>
    </Container>
  )
}
