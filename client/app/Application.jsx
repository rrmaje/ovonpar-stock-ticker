import React from 'react';

import SignIn from './SignIn.jsx';
import SignUp from './SignUp.jsx';
import {
  BrowserRouter as Router,
  Route,
  Switch,
  NavLink,
} from 'react-router-dom';

import Instruments from './Instruments.jsx';
import { CssBaseline } from '@material-ui/core';
import { Box } from '@material-ui/core';
import { Container } from '@material-ui/core';
import { createMuiTheme } from '@material-ui/core/styles';
import { ThemeProvider } from '@material-ui/styles';
import { teal } from '@material-ui/core/colors';

const theme = createMuiTheme({
  palette: {
    primary: teal,
  },
});

export default function Application() {

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="xl">
        <Router>
          <Box my={2}>
            <h1 className="logoheader">Ovonpar Stock Ticker</h1>
            <div className="topnav">
              <NavLink exact activeClassName="active" to="/">Home</NavLink>{' '}
              <NavLink activeClassName="active" to="/signin">Sign In</NavLink>{' '}
            </div>
          </Box>
          <Box my={4}>
            <Switch>
              <Route path="/signin" component={SignIn} />
              <Route path="/signup" component={SignUp} />
              <Route exact path="/" component={Instruments} />
              <Route render={() => <h1>Page not found</h1>} />
            </Switch>
          </Box>
        </Router>
      </Container>
    </ThemeProvider>
  );
}
