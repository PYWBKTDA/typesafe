<<<<<<< HEAD
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import App from './App';
import CourseDetail from './CourseDetail';
import './index.css';
import CourseManagement from './CourseManagement'; 


ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/course/:id" element={<CourseDetail />} />
        <Route path="/manage" element={<CourseManagement />} />  {/* 新增管理页 */}
      </Routes>
    </BrowserRouter>
  </React.StrictMode>,
);
=======
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import App from './App';
import CourseDetail from './CourseDetail';
import './index.css';
import CourseManagement from './CourseManagement'; 
import Profile from "./Profile";
import Select from "./Select";


ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/course/:id" element={<CourseDetail />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/manage" element={<CourseManagement />} />  {/* 新增管理页 */}
        <Route path="/select" element={<Select />} />  
      </Routes>
    </BrowserRouter>
  </React.StrictMode>,
);
>>>>>>> 56873210bdceee95aed3cde7b49554c5fcc480a8
