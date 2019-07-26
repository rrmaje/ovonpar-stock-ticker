import './index.css';

import React from 'react';
import ReactDOM from 'react-dom';
import Application from './Application.jsx';


document.addEventListener('DOMContentLoaded', function () {
  ReactDOM.render(<Application />, document.getElementById("container"));
}, false);

