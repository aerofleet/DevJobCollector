import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'bootstrap/dist/css/bootstrap.min.css';
import './index.css'
import App from './App.jsx'
import './motion/motion.css'
import './motion/devjobs.css'

const motionMode = import.meta.env.VITE_MOTION_MODE;
document.documentElement.dataset.motion = ['off', 'subtle', 'standard'].includes(motionMode) ? motionMode : 'standard';


createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
