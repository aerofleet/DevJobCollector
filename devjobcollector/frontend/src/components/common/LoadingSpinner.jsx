import React from "react";
import '../../styles/LoadingSpinner.css';

const LoadingSpinner = () => {
    return (
        <div className="spinner-container">
            <div className="spinner"></div>
            <p>로딩 중...</p>
        </div>
    );
};

export default LoadingSpinner;