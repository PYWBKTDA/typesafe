// src/App.tsx
import React, { useEffect, useState } from "react";
import { Link, useNavigate, Routes, Route } from "react-router-dom";
import "./App.css";
import LoginForm from "./LoginForm";
import CourseDetail from "./CourseDetail";

// 类型定义
interface Course {
  id: string;
  name: string;
  teacherName: string;
  time: string;
  location: string;
}

const App: React.FC = () => {
  const [user, setUser] = useState<{
    username: string;
    token: string;
    role: "student" | "teacher";
  } | null>(null);

  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const token = localStorage.getItem("token");
    const username = localStorage.getItem("username");
    const role = localStorage.getItem("role") as "student" | "teacher" | null;
    if (token && username && role) {
      setUser({ username, token, role });
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    fetch("/course/mine", {
      headers: {
        Authorization: user.token,
      },
    })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data: Course[] = await res.json();
        setCourses(data);
      })
      .catch((err) => {
        console.error(err);
        setError("加载用户课程失败");
      })
      .finally(() => setLoading(false));
  }, [user]);

  const handleLogout = () => {
    localStorage.clear();
    setUser(null);
    setCourses([]);
  };

  // 未登录时展示登录表单
  if (!user) {
    return <LoginForm onLogin={setUser} />;
  }

  return (
    <div className="app">
      <header className="header">
        <div className="header-top">
          <div className="logo">
            <img
              src="https://upload.wikimedia.org/wikipedia/commons/e/ec/Tsinghua_University_Logo.svg"
              alt="logo"
              className="logo-img"
            />
            <div className="logo-text">
              <h2>清华大学 网络学堂</h2>
              <p>Web Learning</p>
            </div>
          </div>
          <div className="user-settings">
            <span className="username">{user.username}</span>
            <button onClick={handleLogout}>退出登录</button>
          </div>
        </div>
        <nav className="term-nav">
          <a href="#" className="active">
            2024-2025夏季学期课程
          </a>
          <a href="#">2025-2026秋季学期课程</a>
          <a href="#">以前学期课程</a>
          <a href="#">公开课程</a>
        </nav>
        {user.role === "teacher" && (
          <div className="create-course-bar">
            <button onClick={() => navigate("/manage")}>
              进入课程管理
            </button>
          </div>
        )}
      </header>

      <main className="main">
        {loading && <p className="loading">正在加载课程…</p>}
        {error && <p className="error">{error}</p>}
        <div className="course-list">
          <ul>
            {courses.map((course) => (
              <li className="course-card" key={course.id}>
                <div className="course-info">
                  <h3>
                    <Link to={`/course/${course.id}`}>{course.name}</Link> {/* 点击课程名称，跳转到课程详情页面 */}
                  </h3>
                  <span className="teacher">{course.teacherName}</span>
                  <span className="time">{course.time}</span>
                  <span className="location">{course.location}</span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </main>

      <footer className="footer">© 清华大学 网络学堂</footer>
    </div>
  );
};

export default App;
