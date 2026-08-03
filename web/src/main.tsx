import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp, ConfigProvider } from 'antd';
import { BrowserRouter } from 'react-router-dom';
import { App } from './app/App';
import './styles/index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider theme={{ token: { colorPrimary: '#167c5a', borderRadius: 6, fontFamily: 'Inter, "Microsoft YaHei", sans-serif' } }}>
      <AntdApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>
);
