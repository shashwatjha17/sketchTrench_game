import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import './index.css';

import LobbyPage from './pages/LobbyPage';
import GamePage from './pages/GamePage';
import RoomPage from './pages/RoomPage';

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LobbyPage />} />
      <Route path="/rooms/:id" element={<RoomPage />} />
      <Route path="/game/:id" element={<GamePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  </React.StrictMode>,
);
