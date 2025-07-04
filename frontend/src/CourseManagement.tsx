// src/pages/CourseManagement.tsx
import React, { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import DeleteIcon from '@mui/icons-material/Delete';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';

// 类型定义
interface Course {
  id: string;
  name: string;
  uid: string;
  teacherName: string;
  time: string;
  location: string;
}

// 表单验证 schema
const createSchema = z.object({
  name: z.string().min(1, '课程名称不能为空'),
  time: z.string().min(1, '时间不能为空'),
  teacherName: z.string().min(1, '教师姓名不能为空'),
  location: z.string().min(1, '地点不能为空'), // 添加地点验证
});

export default function CourseManagement() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [form, setForm] = useState({ name: '', time: '', teacherName: '', location: '' });
  const [errors, setErrors] = useState<Partial<Record<keyof typeof form, string>>>({});
  const token = localStorage.getItem('token') ?? '';
  const username = localStorage.getItem('username') ?? '';
  const navigate = useNavigate();

  // 拉取用户课程列表
  const fetchMyCourses = async () => {
    try {
      const res = await fetch('/course/mine', {
        headers: { Authorization: token },
      });
      if (!res.ok) throw new Error(`Status ${res.status}`);
      const data: Course[] = await res.json();
      setCourses(data);
    } catch (err) {
      console.error(err);
      alert('加载课程列表失败');
    }
  };

  useEffect(() => {
    fetchMyCourses();
  }, []);

  // 表单输入处理
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setErrors((err) => ({ ...err, [e.target.name]: undefined }));
  };

  // 添加新课程 (老师角色专属)
  const handleAdd = async () => {
    const result = createSchema.safeParse(form);
    if (!result.success) {
      const fieldErrs: Partial<Record<keyof typeof form, string>> = {};
      result.error.errors.forEach((e) => {
        fieldErrs[e.path[0] as keyof typeof form] = e.message;
      });
      setErrors(fieldErrs);
      return;
    }
    try {
      const res = await fetch('/course/create', {
        method: 'POST',
        headers: {
          Authorization: token,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: form.name,
          time: form.time,
          teacher: username,
          teacherName: form.teacherName, // 添加教师姓名
          location: form.location, // 添加课程地点
        }),
      });
      if (!res.ok) throw new Error(await res.text());
      alert('添加成功');
      setForm({ name: '', time: '', teacherName: '', location: '' });
      fetchMyCourses();
    } catch (err: any) {
      alert('添加失败：' + err.message);
    }
  };

  // 删除课程
  const handleDelete = async (courseId: string) => {
    if (!window.confirm('确认删除该课程？')) return;
    try {
      const res = await fetch('/course/delete', {
        method: 'POST',
        headers: {
          Authorization: token,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ courseId }),
      });
      if (!res.ok) throw new Error(await res.text());
      alert('删除成功');
      fetchMyCourses();
    } catch (err: any) {
      alert('删除失败：' + err.message);
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', bgcolor: '#f9f9f9', p: 4 }}>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/')}
        variant="outlined"
        sx={{ mb: 3 }}
      >
        返回主界面
      </Button>

      <Typography variant="h4" gutterBottom>
        课程管理（教师专属）
      </Typography>

      {/* 添加课程表单 */}
      <Box
        component="form"
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 2,
          mb: 4,
          width: '100%', 
          justifyContent: 'space-between',
        }}
        onSubmit={(e) => { e.preventDefault(); handleAdd(); }}
      >
        <TextField
          label="课程名称"
          name="name"
          value={form.name}
          onChange={handleChange}
          error={!!errors.name}
          helperText={errors.name}
          sx={{ flex: 1 }}
        />
        <TextField
          label="上课时间"
          name="time"
          value={form.time}
          onChange={handleChange}
          error={!!errors.time}
          helperText={errors.time}
          sx={{ flex: 1 }}
        />
        <TextField
          label="教师姓名"
          name="teacherName"
          value={form.teacherName}
          onChange={handleChange}
          error={!!errors.teacherName}
          helperText={errors.teacherName}
          sx={{ flex: 1 }}
        />
        <TextField
          label="课程地点"
          name="location"
          value={form.location}
          onChange={handleChange}
          error={!!errors.location}
          helperText={errors.location}
          sx={{ flex: 1 }}
        />
        <Button type="submit" variant="contained">
          添加课程
        </Button>
      </Box>

      <Typography variant="h6" gutterBottom>
        已发布课程（共 {courses.length} 门）
      </Typography>

      {/* 课程列表 */}
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
        {courses.map((c) => (
          <Card
            key={c.id}
            sx={{
              flex: '1 1 calc(50% - 8px)', 
              minWidth: 280, 
              transition: '0.3s', 
              '&:hover': { boxShadow: 3 },
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
            }}
          >
            <CardContent sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box>
                <Typography fontWeight="bold">{c.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  老师：{c.teacherName} / 时间：{c.time} / 地点：{c.location}
                </Typography>
              </Box>
              <IconButton color="error" onClick={() => handleDelete(c.id)}>
                <DeleteIcon />
              </IconButton>
            </CardContent>
          </Card>
        ))}
      </Box>
    </Box>
  );
}
