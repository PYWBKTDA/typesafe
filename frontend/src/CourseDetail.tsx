// src/pages/CourseDetail.tsx
import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, Typography, Container, List, ListItem, ListItemText, Divider } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import "./CourseDetail.css";

interface Course {
  id: string;
  name: string;
  teacherName: string;
  time: string;
  location: string;
}

interface Student {
  uid: string;
  name: string;
}

const CourseDetail: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [course, setCourse] = useState<Course | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [students, setStudents] = useState<Student[]>([]);
  const [studentsLoading, setStudentsLoading] = useState(false);
  const [studentsError, setStudentsError] = useState<string | null>(null);

  const role = localStorage.getItem("role");
  const token = localStorage.getItem("token");

  // ---------- 拉取课程详情 ----------
  useEffect(() => {
    if (!id) return;
    setLoading(true);

    fetch(`/course/info?id=${id}`)
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();
        if (json.status !== "success") throw new Error(json.message);
        setCourse(json.data as Course);
      })
      .catch((err) => {
        console.error(err);
        setError("加载课程详情失败");
      })
      .finally(() => setLoading(false));
  }, [id]);

  // ---------- 如果是教师，再拉选课学生 ----------
  useEffect(() => {
    if (role !== "teacher" || !id || !token) return;

    setStudentsLoading(true);
    console.log(id);
    console.log(token);
    console.log("before fetch");
      fetch(`/course/students?id=${id}`, {
      headers: { Authorization: token }
    })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();
        if (json.status !== "success") throw new Error(json.message);
        const uidList: string[] = json.data;
        const studentObjs = await Promise.all(
          uidList.map(async (uid) => {
            try {
              const res = await fetch(`/user/info?uid=${uid}`, {
                headers: { Authorization: token }
              });
              if (!res.ok) throw new Error(`info HTTP ${res.status}`);
              const infoJson = await res.json();
              if (infoJson.status !== "success") throw new Error(infoJson.message);

              const name = infoJson.data.name ?? "未知用户";
              return { uid, name };
            } catch (err) {
              console.error(`Error fetching info for uid=${uid}:`, err);
              return { uid, name: "加载失败" };
            }
          })
        );

        console.log("resolved student list:", studentObjs);
        setStudents(studentObjs);
      })
      .catch((err) => {
        console.error(err);
        setStudentsError("加载选课学生失败");
      })
      .finally(() => setStudentsLoading(false));
  }, [id, role, token]);

  if (loading) return <p>加载课程信息...</p>;
  if (error) return <p>{error}</p>;

  return (
    <Box className="course-detail-container">
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

        <Typography variant="h6" color="text.secondary" gutterBottom>
          <strong>教师：</strong>{course?.teacherName}
        </Typography>
        <Typography variant="body1" gutterBottom>
          <strong>上课时间：</strong>{course?.time}
        </Typography>
        <Typography variant="body1" gutterBottom>
          <strong>上课地点：</strong>{course?.location}
        </Typography>

        {role === "teacher" && (
          <>
            <Divider sx={{ my: 3 }} />
            <Typography variant="h6" gutterBottom>
              选课学生名单
            </Typography>
            {studentsLoading && <Typography>加载学生列表中...</Typography>}
            {studentsError && <Typography color="error">{studentsError}</Typography>}
            {!studentsLoading && !studentsError && students.length === 0 && (
              <Typography color="text.secondary">暂无学生选课</Typography>
            )}
            <List>
              {students.map((s) => (
                <ListItem key={s.uid} divider>
                  <ListItemText primary={s.name} secondary={`ID: ${s.uid}`} />
                </ListItem>
              ))}
            </List>
          </>
        )}
      </Container>
    </Box>
  );
};

export default CourseDetail;
