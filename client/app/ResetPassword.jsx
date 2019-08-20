import React from 'react';
import { Button } from '@material-ui/core';
import { TextField } from '@material-ui/core';
import { Typography } from '@material-ui/core';
import { makeStyles } from '@material-ui/core/styles';
import { Container } from '@material-ui/core';
import { Box } from '@material-ui/core';
import { Grid } from '@material-ui/core';
import { Link } from 'react-router-dom';

import { Formik } from 'formik';
import * as Yup from 'yup';

import { authenticationService } from '@/_services';

const useStyles = makeStyles(theme => ({
  '@global': {
    body: {
      backgroundColor: theme.palette.common.white,
    },
  },
  paper: {
    marginTop: theme.spacing(8),
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
  },
  form: {
    width: '100%', // Fix IE 11 issue.
    marginTop: theme.spacing(1),
  },
  submit: {
    margin: theme.spacing(3, 0, 2),
  },
  notchedOutline: {
    borderWidth: "1px",
    //borderColor: "green !important"
  },
}));

export default function ResetPassword(props) {

  const classes = useStyles();

  return (

    <Container maxWidth="xs">
      <div className={classes.paper}>
        <Typography component="h1" variant="h5">
          Reset your password
        </Typography>
        <Formik
          initialValues={{
            email: ''
          }}
          validationSchema={Yup.object().shape({
            email: Yup.string().email().required('Email is required'),
          })}
          onSubmit={({ email}, { setStatus, setSubmitting, setErrors }) => {
            setStatus();
            authenticationService.sendResetLink(email)
              .then(
                result => {
                  setSubmitting(false);
                  setStatus(result);
                },
                error => {
                  setSubmitting(false);
                  setErrors({error});
                }
              );
          }}
        >
          {(props) => {
            const {
              values,
              touched,
              errors,
              status,
              isSubmitting,
              handleChange,
              handleSubmit,

            } = props;
            return (

              <form className={classes.form} onSubmit={handleSubmit}>
                {status &&
                  <Box my={2} color="primary.main">

                    {status}
                  </Box>
                }
                {errors &&
                  <Box my={2} color="error.main">

                    {errors.error}
                    
                  </Box>
                }
                {isSubmitting && 
                  <Box my={2} color="primary.main">
                      Sending...
                </Box>
                }
                <TextField InputProps={{
                  classes: {
                    notchedOutline: classes.notchedOutline
                  }
                }} variant="outlined" margin="normal" onChange={handleChange} value={values.email} error={errors.email && touched.email} helperText={(errors.email && touched.email) && errors.email} fullWidth id="email" label="Email Address" name="email" autoFocus />
                <Button type="submit" disabled={isSubmitting} fullWidth variant="contained" color="primary" className={classes.submit}>Send</Button>


              </form>

            );

          }}
        </Formik>
        <Grid container>
          <Grid item>
            <Link to="/auth" variant="body2">
              Sign in with new password
              </Link>
          </Grid>
        </Grid>
      </div>

    </Container>
  )


}
