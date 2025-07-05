import React, { useEffect, useState } from "react";
import "./Profile.css";

interface UserInfo {
  [key: string]: string;
}

const Profile: React.FC = () => {
  const [info, setInfo] = useState<UserInfo | null>(null);
  const [uid, setUid] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 登录态
  const username = localStorage.getItem("username") || "";
  const token = localStorage.getItem("token") || "";

  // 修改密码表单
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const fetchUserInfo = async () => {
      setLoading(true);
      setError(null);
      try {
        // 第一步：获取 UID
        const uidRes = await fetch("/user/uid", {
          headers: { Authorization: token },
        });
        if (!uidRes.ok) throw new Error(`UID error: ${uidRes.status}`);
        const gotUid = (await uidRes.json()).data.uid;
        setUid(gotUid);

        // 第二步：获取用户 info
        const infoRes = await fetch(`/user/info?uid=${gotUid}`);
        if (!infoRes.ok) throw new Error(`Info error: ${infoRes.status}`);
        const data = (await infoRes.json()).data;
        setInfo(data);
      } catch (err: any) {
        setError(err.message || "加载失败");
      } finally {
        setLoading(false);
      }
    };

    fetchUserInfo();
  }, [token]);

  const handleChangePassword = async () => {
    setMessage(null);
    if (!oldPassword || !newPassword || !confirmPassword) {
      setMessage("请填写所有字段。");
      return;
    }
    if (newPassword !== confirmPassword) {
      setMessage("新密码与确认密码不一致。");
      return;
    }
    try {
      const res = await fetch("/user/update", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token,
        },
        body: JSON.stringify({
          oldPassword,
          newPassword,
          confirmPassword,
          info,
        }),
      });
      const result = await res.json();
      console.log(result.status);
      if (!res.ok) throw new Error(result.message);
      setMessage(result.message || "修改成功！");
      setOldPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err: any) {
      setMessage(err.message || "请求失败");
    }
  };

  return (
    <div className="profile-fullscreen">
      <div className="profile-card">
        <h1>个人中心</h1>
        {loading && <p>正在加载...</p>}
        {error && <p className="error">{error}</p>}

        {!loading && !error && (
          <div className="info-block">
            <p><strong>账号名称（本地）：</strong> {username}</p>
            {uid && <p><strong>UID：</strong> {uid}</p>}
            {info && Object.entries(info).map(([key, value]) => (
              <p key={key}><strong>{key}：</strong> {value}</p>
            ))}
          </div>
        )}

        <h2>修改密码</h2>
        <div className="password-form">
          <input
            type="password"
            placeholder="原密码"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
          />
          <input
            type="password"
            placeholder="新密码"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <input
            type="password"
            placeholder="确认新密码"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
          <button onClick={handleChangePassword}>提交修改</button>
        </div>

        {message && <p className="message">{message}</p>}

        <button className="back-button" onClick={() => window.history.back()}>返回</button>
      </div>
    </div>
  );
};

export default Profile;
