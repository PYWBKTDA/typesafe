// src/pages/CourseDetail.tsx
import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, Typography, Container } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import "./CourseDetail.css";

interface Course {
  id: string;
  name: string;
  teacherName: string;
  time: string;
  location: string;
}

const CourseDetail: React.FC = () => {
  const { id } = useParams();                 // 路由里的 :id
  const [course, setCourse] = useState<Course | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  /* ---------- 拉取课程详情 ---------- */
  useEffect(() => {
    if (!id) return;
    setLoading(true);

    fetch(`/course/info?id=${id}`)
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();         // ← 先取整体 JSON
        if (json.status !== "success") throw new Error(json.message);
        setCourse(json.data as Course);        // ← 再取 data 字段
      })
      .catch((err) => {
        console.error(err);
        setError("加载课程详情失败");
      })
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <p>加载课程信息...</p>;
  if (error)   return <p>{error}</p>;

  return (
    <Box className="course-detail-container">
      {/* 返回按钮 */}
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate("/")}
        variant="outlined"
        className="back-button"
      >
        返回课程列表
      </Button>

      <Container className="course-info-container">
        <Typography variant="h4" gutterBottom>
          {course?.name}
        </Typography>

        {/* 各字段分行展示 */}
        <Typography variant="h6" color="text.secondary" gutterBottom>
          <strong>教师：</strong>{course?.teacherName}
        </Typography>
        <Typography variant="body1" gutterBottom>
          <strong>上课时间：</strong>{course?.time}
        </Typography>
        <Typography variant="body1" gutterBottom>
          <strong>上课地点：</strong>{course?.location}
        </Typography>
      </Container>
    </Box>
  );
};

export default CourseDetail;