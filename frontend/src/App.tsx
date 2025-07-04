// src/App.tsx
import React, { useEffect, useState } from "react";
import {
  Link,
  useNavigate,
  Routes,
  Route,
} from "react-router-dom";

import LoginForm     from "./LoginForm";
import CourseDetail  from "./CourseDetail";
import Profile      from "./Profile";
import "./App.css";

/* ---------- 类型 ---------- */
interface Course {
  id: string;
  name: string;
  teacherName: string;
  time: string;
  location: string;
}

const App: React.FC = () => {
  /* ---------- 登录状态 ---------- */
  const [user, setUser] = useState<{
    username: string;
    token: string;
    role: "student" | "teacher";
  } | null>(null);

  /* ---------- 课程数据 ---------- */
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);

  const navigate = useNavigate();

  /* ---------- 从 localStorage 读取登录态 ---------- */
  useEffect(() => {
    const token = localStorage.getItem("token");
    console.log(token);
    const username = localStorage.getItem("username");
    const role = localStorage.getItem("role") as "student" | "teacher" | null;
    if (token && username && role) setUser({ username, token, role });
  }, []);

  /* ---------- 拉取并筛选课程 ---------- */
  useEffect(() => {
    if (!user) return;

    (async () => {
      setLoading(true);
      setError(null);

      try {
        /* ① 取 uid */
        const uidRes = await fetch("/user/uid", {
          headers: { Authorization: user.token },
        });
        if (!uidRes.ok) throw new Error(`UID HTTP ${uidRes.status}`);
        const uid: string = (await uidRes.json()).data.uid;

        /* ② 拉全部课程 */
        const listRes = await fetch("/course/list");
        if (!listRes.ok) throw new Error(`List HTTP ${listRes.status}`);
        const allCourses: Course[] = (await listRes.json()).data;

        /* ③ 过滤自己相关的课程 */
        const filtered: Course[] = [];
        await Promise.all(
          allCourses.map(async (c) => {
            const chk = await fetch(`/course/check?courseId=${c.id}&uid=${uid}`);
            if (!chk.ok) return;
            const status: string = (await chk.json()).data.status;
            console.log(status);
            if (
              (user.role === "student" && status === "Selected") ||
              (user.role === "teacher" && status === "Created")
            ) filtered.push(c);
          })
        );
        setCourses(filtered);
      } catch {
        setError("加载全部课程失败");
      } finally { setLoading(false); }
    })();
  }, [user]);

  /* ---------- 退出 ---------- */
  const handleLogout = () => {
    localStorage.clear();
    setUser(null);
    setCourses([]);
  };

  /* ---------- 未登录：显示登录表单 ---------- */
  if (!user) return <LoginForm onLogin={setUser} />;

  /* ---------- 已登录：主界面 + 详情 / 个人中心 路由 ---------- */
  return (
    <Routes>
      {/* 默认：课程列表 */}
      <Route
        path="/*"
        element={
          <div className="app">
            {/* ===== 头部 ===== */}
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

                {/* ---------- 用户名可点击 ---------- */}
                <div className="user-settings">
                  <span
                    className="username"
                    style={{ cursor: "pointer" }}
                    onClick={() => navigate("/profile")}
                  >
                    {user.username}
                  </span>
                  <button onClick={handleLogout}>退出登录</button>
                </div>
              </div>

              <nav className="term-nav">
                <a href="#" className="active">2024-2025夏季学期课程</a>
                <a href="#">2025-2026秋季学期课程</a>
                <a href="#">以前学期课程</a>
                <a href="#">公开课程</a>
              </nav>

              <div className="create-course-bar">
                {user.role === "teacher" && (
                  <button onClick={() => navigate("/manage")}>
                    进入课程管理
                  </button>
                )}
                {user.role === "student" && (
                  <button onClick={() => navigate("/select")}>
                    选课管理
                  </button>
                )}
              </div>


            </header>

            {/* ===== 主体 ===== */}
            <main className="main">
              {loading && <p className="loading">正在加载课程…</p>}
              {error   && <p className="error">{error}</p>}

              <div className="course-list">
                <ul>
                  {courses.map((c) => (
                    <li className="course-card" key={c.id}>
                      <div className="course-info">
                        <h3>
                          <Link to={`/course/${c.id}`}>{c.name}</Link>
                        </h3>
                        <span className="teacher">{c.teacherName}</span>
                        <span className="time">{c.time}</span>
                        <span className="location">{c.location}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            </main>

            <footer className="footer">© 清华大学 网络学堂</footer>
          </div>
        }
      />
    </Routes>
  );
};

export default App;