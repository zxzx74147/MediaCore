#extension GL_OES_EGL_image_external : require
precision lowp float;

varying highp vec2 textureCoordinate;
uniform samplerExternalOES sTexture;

uniform sampler2D inputImageTexture2;

void main()
{

     highp vec4 textureColor = texture2D(sTexture, textureCoordinate);

     highp float blueColor = textureColor.b * 15.0;

     highp vec2 quad1;
     quad1.y = floor(floor(blueColor) / 4.0);
     quad1.x = floor(blueColor) - (quad1.y * 4.0);

     highp vec2 quad2;
     quad2.y = floor(ceil(blueColor) / 4.0);
     quad2.x = ceil(blueColor) - (quad2.y * 4.0);

     highp vec2 texPos1;
     texPos1.x = (quad1.x * 0.25) + 0.5/64.0 + ((0.25 - 1.0/64.0) * textureColor.r);
     texPos1.y = (quad1.y * 0.25) + 0.5/64.0 + ((0.25 - 1.0/64.0) * textureColor.g);

     highp vec2 texPos2;
     texPos2.x = (quad2.x * 0.25) + 0.5/64.0 + ((0.25 - 1.0/64.0) * textureColor.r);
     texPos2.y = (quad2.y * 0.25) + 0.5/64.0 + ((0.25 - 1.0/64.0) * textureColor.g);

     lowp vec4 newColor1 = texture2D(inputImageTexture2, texPos1);
     lowp vec4 newColor2 = texture2D(inputImageTexture2, texPos2);

     lowp vec4 newColor = mix(newColor1, newColor2, fract(blueColor));
//     gl_FragColor = mix(textureColor, vec4(newColor.rgb, textureColor.w), 0.5);
//     gl_FragColor = texture2D(inputImageTexture2, textureCoordinate);
//textureCoordinate.x-=0.1;
     highp vec2 pos;
//     pos.x = textureCoordinate.x-0.1;
//     pos.y = textureCoordinate.y;
//     textureColor = texture2D(sTexture, pos);
     float color = textureColor.r * 0.3 + textureColor.g * 0.59 + textureColor.b * 0.11;
     gl_FragColor = vec4(color, color, color, 1.0);

}