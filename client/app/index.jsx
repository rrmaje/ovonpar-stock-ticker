import './index.css';

import React from 'react';
import ReactDOM from 'react-dom';
import Application from './Application.jsx';

// setup backend for development
import { configureInMemoryBackend} from '@/_helpers';
configureInMemoryBackend()

document.addEventListener('DOMContentLoaded', function () {
  ReactDOM.render(<Application />, document.getElementById("container"));
}, false);

