// src/pages/Select.tsx
import React, { useEffect, useState } from 'react';
import {
  Box, Button, Card, CardContent, Typography
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';
import './Select.css';

/* ---------- 类型 ---------- */
interface Course {
  id: string;
  name: string;
  teacherName: string;
  time: string;
  location: string;
}

export default function SelectPage() {
  /* ---------- 状态 ---------- */
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const token = localStorage.getItem('token');
  const navigate = useNavigate();

  /* ---------- 拉取全部课程 ---------- */
  const fetchAllCourses = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/course/list');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setCourses(json.data as Course[]);
    } catch (err: any) {
      console.error(err);
      setError('加载课程列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAllCourses();
  }, []);

  /* ---------- 选课 ---------- */
  const handleSelect = async (courseId: string) => {
    try {
      const res = await fetch('/course/select', {
        method: 'POST',
        headers: {
          Authorization: token || '',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ courseId }),
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.message);
      setMessage(json.message || '选课成功');
    } catch (err: any) {
      setMessage(err.message || '选课失败');
    }
  };

  /* ---------- 退课 ---------- */
  const handleDrop = async (courseId: string) => {
    try {
      const res = await fetch('/course/drop', {
        method: 'POST',
        headers: {
          Authorization: token || '',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ courseId }),
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.message);
      setMessage(json.message || '退课成功');
    } catch (err: any) {
      setMessage(err.message || '退课失败');
    }
  };

  /* ---------- UI ---------- */
  return (
    <Box className="select-container">
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/')}
        variant="outlined"
        className="return-button"
      >
        返回主界面
      </Button>

      <Typography className="select-title">
        选课管理
      </Typography>

      {loading && <p>正在加载课程列表...</p>}
      {error && <p className="error">{error}</p>}
      {message && <p className="message">{message}</p>}

      <Box className="course-list">
        {courses.map((c) => (
          <Card key={c.id} className="course-card">
            <CardContent className="card-content">
              <Box className="card-info">
                <Typography
                  variant="h6"
                  className="course-title-link"
                  onClick={() => navigate(`/course/${c.id}`)}
                >
                  {c.name}
                </Typography>
                <span>老师：{c.teacherName}</span>
                <div className="card-meta">
                  <span>时间：{c.time}</span>
                  <span>地点：{c.location}</span>
                </div>
              </Box>
              <Box className="action-buttons">
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => handleSelect(c.id)}
                >
                  选课
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  size="small"
                  onClick={() => handleDrop(c.id)}
                >
                  退课
                </Button>
              </Box>
            </CardContent>
          </Card>
        ))}
      </Box>
    </Box>
  );
}
