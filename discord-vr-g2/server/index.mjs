import {createApp} from './app.mjs';
const app=createApp({origin:process.env.PUBLIC_ORIGIN,clientId:process.env.DISCORD_CLIENT_ID,secret:process.env.DISCORD_CLIENT_SECRET});
const port=Number(process.env.PORT)||5173;
app.listen(port,'0.0.0.0',()=>console.log(`Discord VR G2 preview listening on 0.0.0.0:${port}. OAuth ${process.env.DISCORD_CLIENT_ID&&process.env.DISCORD_CLIENT_SECRET&&process.env.PUBLIC_ORIGIN?'configured':'disabled: credentials/origin not configured'}.`));
