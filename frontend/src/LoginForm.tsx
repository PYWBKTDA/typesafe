import React, { useState } from "react";
import "./LoginForm.css";

interface Props {
  onLogin: (user: {
    username: string;
    token: string;
    role: "student" | "teacher";
  }) => void;
}

const LoginForm: React.FC<Props> = ({ onLogin }) => {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [role, setRole] = useState<"student" | "teacher">("student");
  const [name, setName] = useState("");
  const [department, setDepartment] = useState("");
  const [id, setId] = useState(""); // 学号或工号

  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    try {
      const endpoint = mode === "login" ? "/user/login" : "/user/register";
      const headers = { "Content-Type": "application/json" };

      const body =
        mode === "login"
          ? { username, password }
          : {
              username,
              password,
              confirmPassword,
              info:
                role === "student"
                  ? {
                      studentId: id,
                      name,
                      department,
                      type: "student",
                    }
                  : {
                      teacherId: id,
                      name,
                      department,
                      type: "teacher",
                    },
            };

      const res = await fetch(endpoint, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        const msg = await res.text();
        throw new Error(msg || "请求失败");
      }
      if(mode==="login"){
        const data = await res.json(); // { status, message, data }
        if(data.status==="error"){
          throw new Error(data.message || "请求失败");
        }
        localStorage.setItem("token", data.data.token);
        const uidRes = await fetch("/user/uid", {
          headers: { Authorization: data.data.token },
        });
        if (!uidRes.ok) {
          const txt = await uidRes.text();
          throw new Error(txt || `HTTP ${uidRes.status}`);
        }
        const uidJson = await uidRes.json();
        if (uidJson.status !== "success" || !uidJson.data?.uid) {
          throw new Error(uidJson.message || "获取 UID 失败");
        }
        const uid = uidJson.data.uid as string;

        const infoRes = await fetch(`/user/info?uid=${encodeURIComponent(uid)}`, {
          headers: { Authorization: data.data.token },
        });
        if (!infoRes.ok) {
          const txt = await infoRes.text();
          throw new Error(txt || `HTTP ${infoRes.status}`);
        }
        const infoJson = await infoRes.json();

        if (infoJson.status !== "success"|| !infoJson.data.type) {
          throw new Error(infoJson.message || "获取用户信息失败");
        }
        const userRole = infoJson.data.type as "student" | "teacher";

        localStorage.setItem("username", username);
        localStorage.setItem("role", userRole);
        onLogin({ username: username, token: data.data.token, role: userRole });
      }
      if(mode==="register"){
        const data = await res.json();
        if(data.message=="error"){
          throw new Error(data.message || "请求失败");
        }
        alert("注册成功，请使用账号密码登录");
        setPassword("");
        setMode("login");
        return;                      
      }
    } catch (err: any) {
      setError(err.message || "发生错误");
    }
  };

  return (
    <div className="login-form">
      <div className="login-box">
        <h2 className="login-title">{mode === "login" ? "登录网络学堂" : "注册账号"}</h2>
        {error && <p className="error">{error}</p>}

        <form onSubmit={handleSubmit}>
          <input
            type="text"
            placeholder="用户名"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
          <input
            type="password"
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {mode === "register" && (
            <>
              <input
                type="password"
                placeholder="确认密码"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as "student" | "teacher")}
              >
                <option value="student">学生</option>
                <option value="teacher">老师</option>
              </select>
              <input
                type="text"
                placeholder={role === "student" ? "学号" : "工号"}
                value={id}
                onChange={(e) => setId(e.target.value)}
                required
              />
              <input
                type="text"
                placeholder="姓名"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
              <input
                type="text"
                placeholder="院系"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                required
              />
            </>
          )}

          <button type="submit">{mode === "login" ? "登录" : "注册"}</button>
        </form>

        <div style={{ marginTop: "1rem" }}>
          {mode === "login" ? (
            <>
              没有账号？{" "}
              <button type="button" onClick={() => setMode("register")}>
                点击注册
              </button>
            </>
          ) : (
            <>
              已有账号？{" "}
              <button type="button" onClick={() => setMode("login")}>
                去登录
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default LoginForm;