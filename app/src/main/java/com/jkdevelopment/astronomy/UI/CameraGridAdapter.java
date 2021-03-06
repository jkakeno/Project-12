package com.jkdevelopment.astronomy.UI;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.jkdevelopment.astronomy.Model.RoverList;
import com.jkdevelopment.astronomy.R;

public class CameraGridAdapter extends BaseAdapter {
    Context context;
    RoverList roverList;
    private static LayoutInflater inflater=null;
    public int selectedCamera=-1;

    public CameraGridAdapter(Context context, RoverList roverList) {
        this.context = context;
        this.roverList=roverList;
    }

    public int getCount() {
        return roverList.getRoverList().get(0).getCameraList().size();
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View roverGrid;
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        String camera = roverList.getRoverList().get(0).getCameraList().get(position);

        if (convertView == null) {
            roverGrid=new View(context);
            roverGrid=inflater.inflate(R.layout.camera_grid, null);
            TextView cameraName_tv = (TextView) roverGrid.findViewById(R.id.camera_name);
            cameraName_tv.setText(camera);
        } else {
            roverGrid = (View) convertView;
        }

        if(selectedCamera == position){
            roverGrid.setBackgroundColor(Color.RED);
        }else{
            roverGrid.setBackgroundColor(Color.TRANSPARENT);
        }

        return roverGrid;
    }
}