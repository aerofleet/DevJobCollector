import { StrictMode } from 'react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import 'bootstrap/dist/css/bootstrap-reboot.min.css';
import 'bootstrap/dist/css/bootstrap-grid.min.css';
import './index.css'
import App from './App.jsx'
import './motion/motion.css'
import './motion/devjobs.css'

const motionMode = import.meta.env.VITE_MOTION_MODE;
document.documentElement.dataset.motion = ['off', 'subtle', 'standard'].includes(motionMode) ? motionMode : 'standard';


const root = document.getElementById('root');
const app = <StrictMode><App /></StrictMode>;
if (window.location.pathname === '/' && root.hasChildNodes()) hydrateRoot(root, app);
else createRoot(root).render(app);
