import React from 'react';
import '../../styles/TechStackBadge.css';

const TechStackBadge = ({ tech }) => {
    return (
        <span className='tech-badge'>
            { tech.stackName }
        </span>
    );
};

export default TechStackBadge;